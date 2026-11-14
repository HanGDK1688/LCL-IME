package com.lcl.ime;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.inputmethodservice.InputMethodService;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class LclImeService extends InputMethodService {
    private Board board;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final Runnable longDeleteRunnable = new Runnable() {
        @Override public void run() {
            if (board != null) board.handleLongDelete();
        }
    };

    @Override public View onCreateInputView() {
        board = new Board(this);
        return board;
    }

    @Override public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        if (board != null) board.invalidate();
    }

    void commitText(String text) {
        if (text == null || text.isEmpty()) return;
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) ic.commitText(text, 1);
    }

    void setComposingText(String text) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        if (text == null || text.isEmpty()) ic.finishComposingText();
        else ic.setComposingText(text, 1);
    }

    void finishComposingText() {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) ic.finishComposingText();
    }

    void deleteOneChar() {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) ic.deleteSurroundingText(1, 0);
    }

    void clearCurrentText() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        CharSequence before = ic.getTextBeforeCursor(10000, 0);
        CharSequence after = ic.getTextAfterCursor(10000, 0);
        int beforeLength = before == null ? 0 : before.length();
        int afterLength = after == null ? 0 : after.length();
        if (beforeLength == 0 && afterLength == 0) return;
        ic.beginBatchEdit();
        try {
            ic.deleteSurroundingText(beforeLength, afterLength);
        } finally {
            ic.endBatchEdit();
        }
    }

    void sendEnter() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        ic.sendKeyEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_ENTER));
        ic.sendKeyEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_ENTER));
    }

    class Board extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final String[] modeLabels = {"26键", "T9", "数字", "符号", "手写"};
        private final Map<String, String[]> dictionary = new HashMap<>();
        private final Map<Character, Character> traditionalMap = new HashMap<>();
        private String currentMode = "26键", theme = "浅色", language = "中", script = "简";
        private boolean shift, menuOpen;
        private String composing = "", t9Buffer = "", lastT9Key = "";
        private long lastT9TapAt;
        private ArrayList<String> candidates = new ArrayList<>();
        private ArrayList<Float> strokeX = new ArrayList<>(), strokeY = new ArrayList<>();
        private int bgColor, keyColor, textColor, accentColor, accentTextColor;
        private float width, height, topBarHeight, bottomBarHeight, candidateHeight, keyboardHeight;

        Board(Context context) {
            super(context);
            initDictionary();
            initTraditionalMap();
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }

        private void initDictionary() {
            dictionary.put("ni", new String[]{"你", "泥", "尼", "呢", "拟", "逆", "妮"});
            dictionary.put("hao", new String[]{"好", "号", "浩", "豪", "耗", "毫"});
            dictionary.put("wo", new String[]{"我", "握", "窝", "喔", "沃"});
            dictionary.put("shi", new String[]{"是", "时", "事", "市", "试", "识"});
            dictionary.put("de", new String[]{"的", "得", "地", "德", "底"});
            dictionary.put("ma", new String[]{"吗", "妈", "马", "嘛", "麻"});
            dictionary.put("zhong", new String[]{"中", "钟", "众", "重", "终"});
            dictionary.put("guo", new String[]{"国", "过", "果", "锅", "郭"});
            dictionary.put("ren", new String[]{"人", "认", "忍", "仁"});
            dictionary.put("tian", new String[]{"天", "田", "甜", "填", "舔"});
            dictionary.put("qi", new String[]{"起", "其", "气", "七", "期"});
            dictionary.put("xie", new String[]{"写", "些", "谢", "卸", "斜"});
            dictionary.put("nihao", new String[]{"你好", "您好", "你好吗"});
            dictionary.put("zhongguo", new String[]{"中国", "中国人", "中华"});
            dictionary.put("yi", new String[]{"一", "以", "意", "已", "易"});
            dictionary.put("er", new String[]{"二", "而", "儿", "额", "耳"});
        }

        private void initTraditionalMap() {
            traditionalMap.put('国', '國'); traditionalMap.put('来', '來'); traditionalMap.put('为', '為');
            traditionalMap.put('与', '與'); traditionalMap.put('这', '這'); traditionalMap.put('里', '裡');
            traditionalMap.put('云', '雲'); traditionalMap.put('万', '萬'); traditionalMap.put('汉', '漢');
            traditionalMap.put('语', '語'); traditionalMap.put('龙', '龍'); traditionalMap.put('门', '門');
            traditionalMap.put('马', '馬'); traditionalMap.put('后', '後'); traditionalMap.put('发', '發');
        }

        private void updateThemeColors() {
            if ("深色".equals(theme)) {
                bgColor=0xFF202124; keyColor=0xFF2E3136; textColor=Color.WHITE; accentColor=0xFF8AB4F8; accentTextColor=Color.WHITE;
            } else if ("蓝色".equals(theme)) {
                bgColor=0xFFEAF3FF; keyColor=Color.WHITE; textColor=0xFF183A5B; accentColor=0xFF3A7FE8; accentTextColor=Color.WHITE;
            } else if ("绿色".equals(theme)) {
                bgColor=0xFFEAF7EE; keyColor=Color.WHITE; textColor=0xFF18472D; accentColor=0xFF2E9C5A; accentTextColor=Color.WHITE;
            } else {
                bgColor=0xFFF2F3F5; keyColor=Color.WHITE; textColor=0xFF1F2328; accentColor=0xFF4C83D8; accentTextColor=Color.WHITE;
            }
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            width=getWidth(); height=getHeight(); updateThemeColors();
            topBarHeight=height*.14f; candidateHeight=candidates.isEmpty()?0:height*.12f;
            bottomBarHeight=height*.16f; keyboardHeight=height-topBarHeight-candidateHeight-bottomBarHeight;
            canvas.drawColor(bgColor); drawTopBar(canvas); drawCandidateBar(canvas); drawMainArea(canvas); drawBottomBar(canvas);
            if (menuOpen) drawModeMenu(canvas);
        }

        private void drawTopBar(Canvas c) {
            float cell=width/4f; String[] labels={"键盘","主题","剪贴板","隐藏"};
            for(int i=0;i<4;i++){float l=i*cell+4,r=(i+1)*cell-4; rounded(c,l,8,r,topBarHeight-8,keyColor); text(c,labels[i],(l+r)/2,topBarHeight*.58f,textColor,16,Paint.Align.CENTER);}
        }
        private void drawCandidateBar(Canvas c) {
            if(candidates.isEmpty())return;
            float y=topBarHeight+candidateHeight*.58f;
            if(!composing.isEmpty())text(c,composing+" ",12,y,accentColor,16,Paint.Align.LEFT);
            float start=Math.max(90,width*.32f), slot=(width-start)/Math.max(1,Math.min(6,candidates.size()));
            for(int i=0;i<Math.min(6,candidates.size());i++)text(c,candidates.get(i),start+(i+.5f)*slot,y,textColor,17,Paint.Align.CENTER);
        }
        private void drawMainArea(Canvas c) {
            float y=topBarHeight+candidateHeight;
            if("手写".equals(currentMode)){drawHandwrite(c,y);return;}
            String[][] rows=keyRows(); float rowH=keyboardHeight/rows.length;
            for(int r=0;r<rows.length;r++){
                float cell=width/rows[r].length;
                for(int col=0;col<rows[r].length;col++){
                    float l=col*cell+4, t=y+r*rowH+4, right=(col+1)*cell-4, b=y+(r+1)*rowH-6;
                    String label=rows[r][col];
                    if("26键".equals(currentMode)&&shift)label=label.toUpperCase(Locale.ROOT);
                    rounded(c,l,t,right,b,keyColor); text(c,label,(l+right)/2,(t+b)/2+10,textColor,18,Paint.Align.CENTER);
                }
            }
        }
        private String[][] keyRows() {
            if("26键".equals(currentMode))return new String[][]{{"q","w","e","r","t","y","u","i","o","p"},{"a","s","d","f","g","h","j","k","l"},{"z","x","c","v","b","n","m"}};
            if("T9".equals(currentMode))return new String[][]{{"1","2","3"},{"4","5","6"},{"7","8","9"},{"*","0","#"}};
            if("数字".equals(currentMode))return new String[][]{{"1","2","3"},{"4","5","6"},{"7","8","9"},{"-","0","."}};
            return new String[][]{{"。","，","？","！","；","："},{"、","…","（","）","“","”"},{"《","》","—","￥","@","/"},{"！","？","%","&","*","…"}};
        }
        private void drawHandwrite(Canvas c,float y) {
            float top=y+8,bottom=height-bottomBarHeight-8; rounded(c,8,top,width-8,bottom,0xFFCED4DC);
            text(c,"手写区：画字后松手识别",width/2,(top+bottom)/2,textColor,15,Paint.Align.CENTER);
            float l=width-110, t=bottom-54, r=width-12, b=bottom-12; rounded(c,l,t,r,b,keyColor); text(c,"清空",(l+r)/2,b-22,textColor,14,Paint.Align.CENTER);
            if(!strokeX.isEmpty()){paint.setStyle(Paint.Style.STROKE);paint.setColor(accentColor);paint.setStrokeWidth(4);for(int i=1;i<strokeX.size();i++)c.drawLine(strokeX.get(i-1),strokeY.get(i-1),strokeX.get(i),strokeY.get(i),paint);paint.setStyle(Paint.Style.FILL);}
        }
        private void drawBottomBar(Canvas c) {
            String[] labels={language,script,shift?"SHIFT↑":"SHIFT","空格","⌫","↵"}; float[] widths={.12f,.12f,.15f,.27f,.14f,.20f};float x=0;
            for(int i=0;i<labels.length;i++){float cell=width*widths[i],l=x+2,r=x+cell-2,t=height-bottomBarHeight+4,b=height-4;int fill=i==3?accentColor:keyColor,tint=i==3?accentTextColor:textColor;rounded(c,l,t,r,b,fill);text(c,labels[i],(l+r)/2,(t+b)/2+8,tint,17,Paint.Align.CENTER);x+=cell;}
        }
        private void drawModeMenu(Canvas c) {
            float x=10,y=topBarHeight+8,w=width*.42f,h=keyboardHeight*.62f;rounded(c,x,y,x+w,y+h,keyColor);
            for(int i=0;i<modeLabels.length;i++)text(c,modeLabels[i],x+w/2,y+22+i*h/modeLabels.length,textColor,16,Paint.Align.CENTER);
        }
        private void rounded(Canvas c,float l,float t,float r,float b,int color){paint.setStyle(Paint.Style.FILL);paint.setColor(color);c.drawRoundRect(new RectF(l,t,r,b),12,12,paint);}
        private void text(Canvas c,String s,float x,float y,int color,float size,Paint.Align align){paint.setStyle(Paint.Style.FILL);paint.setColor(color);paint.setTextAlign(align);paint.setTextSize(size);paint.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));c.drawText(s,x,y,paint);}

        private void refreshCandidates(){
            candidates.clear();
            if(!composing.isEmpty()){String[] values=dictionary.get(composing.toLowerCase(Locale.ROOT));if(values!=null)candidates.addAll(Arrays.asList(values));else if(composing.matches("[a-z]+"))candidates.add(composing);}
            invalidate();
        }
        private String toTraditional(String value){StringBuilder out=new StringBuilder();for(int i=0;i<value.length();i++){char ch=value.charAt(i);Character mapped=traditionalMap.get(ch);out.append(mapped==null?ch:mapped);}return out.toString();}
        private void submitCandidate(int index){
            if(index<0||index>=candidates.size())return;String value=candidates.get(index);if("繁".equals(script))value=toTraditional(value);
            finishComposingText();commitText(value);composing="";t9Buffer="";lastT9Key="";candidates.clear();invalidate();
        }
        private void addInput(String value){
            if("En".equals(language)){commitText(shift?value.toUpperCase(Locale.ROOT):value.toLowerCase(Locale.ROOT));shift=false;invalidate();return;}
            if("T9".equals(currentMode)){addT9(value);return;}
            if("26键".equals(currentMode)&&value.matches("[a-zA-Z]")){composing+=value.toLowerCase(Locale.ROOT);setComposingText(composing);refreshCandidates();}else commitText(value);
        }
        private void addT9(String digit){
            if(!digit.matches("[2-9]"))return;String[] groups={"","","abc","def","ghi","jkl","mno","pqrs","tuv","wxyz"};String group=groups[Integer.parseInt(digit)];long now=System.currentTimeMillis();
            if(lastT9Key.equals(digit)&&now-lastT9TapAt<800&&!t9Buffer.isEmpty()){int i=group.indexOf(t9Buffer.charAt(t9Buffer.length()-1));t9Buffer=String.valueOf(group.charAt((i+1)%group.length()));}else t9Buffer=group.substring(0,1);
            lastT9Key=digit;lastT9TapAt=now;composing+=t9Buffer;setComposingText(composing);refreshCandidates();
        }
        private void handleTop(int index){
            if(index==0)menuOpen=!menuOpen;
            else if(index==1)theme="浅色".equals(theme)?"深色":"深色".equals(theme)?"蓝色":"蓝色".equals(theme)?"绿色":"浅色";
            else if(index==2){ClipboardManager cm=(ClipboardManager)getContext().getSystemService(Context.CLIPBOARD_SERVICE);if(cm!=null&&cm.hasPrimaryClip()){ClipData d=cm.getPrimaryClip();if(d!=null&&d.getItemCount()>0){CharSequence s=d.getItemAt(0).coerceToText(getContext());if(s!=null)commitText(s.toString());}}}
            else if(index==3)requestHideSelf(0);invalidate();
        }
        private void selectMode(int index){if(index<0||index>=modeLabels.length)return;currentMode=modeLabels[index];menuOpen=false;composing="";t9Buffer="";lastT9Key="";candidates.clear();finishComposingText();invalidate();}
        private void handleBottom(int index){
            if(index==0){language="中".equals(language)?"En":"中";composing="";t9Buffer="";lastT9Key="";candidates.clear();finishComposingText();}
            else if(index==1){script="简".equals(script)?"繁":"简";}
            else if(index==2)shift=!shift;
            else if(index==3){if("中".equals(language)&&!composing.isEmpty()&&!candidates.isEmpty())submitCandidate(0);else commitText(" ");}
            else if(index==4){if(!composing.isEmpty()){composing=composing.substring(0,composing.length()-1);setComposingText(composing);refreshCandidates();}else deleteOneChar();}
            else sendEnter();invalidate();
        }
        void handleLongDelete(){composing="";t9Buffer="";lastT9Key="";candidates.clear();finishComposingText();clearCurrentText();invalidate();}
        private int bottomIndex(float x){float[] cuts={.12f,.24f,.39f,.66f,.82f,1f};for(int i=0;i<cuts.length;i++)if(x<=width*cuts[i])return i;return 5;}

        @Override public boolean onTouchEvent(MotionEvent e){
            float x=e.getX(),y=e.getY();int action=e.getAction();
            if(action==MotionEvent.ACTION_DOWN&&y>=height-bottomBarHeight&&x>=width*.82f&&x<=width*.96f){uiHandler.removeCallbacks(longDeleteRunnable);uiHandler.postDelayed(longDeleteRunnable,500);}
            if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL)uiHandler.removeCallbacks(longDeleteRunnable);
            if(action!=MotionEvent.ACTION_UP){
                if("手写".equals(currentMode)&&y>topBarHeight+candidateHeight&&y<height-bottomBarHeight){if(action==MotionEvent.ACTION_DOWN){strokeX.clear();strokeY.clear();}if(action==MotionEvent.ACTION_DOWN||action==MotionEvent.ACTION_MOVE){strokeX.add(x);strokeY.add(y);invalidate();return true;}}
                return true;
            }
            if(y<topBarHeight){handleTop((int)(x/(width/4f)));return true;}
            if(menuOpen&&y>=topBarHeight&&y<=topBarHeight+keyboardHeight*.62f){int i=(int)((y-topBarHeight-8)/(keyboardHeight*.62f/modeLabels.length));selectMode(i);return true;}
            if(!candidates.isEmpty()&&y>=topBarHeight&&y<topBarHeight+candidateHeight){float start=Math.max(90,width*.32f);int i=(int)((x-start)/((width-start)/Math.max(1,Math.min(6,candidates.size()))));submitCandidate(Math.max(0,Math.min(candidates.size()-1,i)));return true;}
            if(y>=height-bottomBarHeight){handleBottom(bottomIndex(x));return true;}
            if("手写".equals(currentMode)&&y>topBarHeight+candidateHeight&&y<height-bottomBarHeight){if(x>width-110&&y>height-bottomBarHeight-54){strokeX.clear();strokeY.clear();candidates.clear();invalidate();return true;}if(strokeX.size()>8){candidates.clear();candidates.addAll(Arrays.asList("一","二","口","人","大","中"));invalidate();}return true;}
            if(y>topBarHeight+candidateHeight&&y<height-bottomBarHeight){String[][] rows=keyRows();int row=Math.max(0,Math.min(rows.length-1,(int)((y-topBarHeight-candidateHeight)/(keyboardHeight/rows.length))));int col=Math.max(0,Math.min(rows[row].length-1,(int)(x/(width/rows[row].length))));addInput(rows[row][col]);return true;}
            return true;
        }
    }
}
