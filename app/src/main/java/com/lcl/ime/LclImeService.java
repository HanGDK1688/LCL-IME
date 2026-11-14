package com.lcl.ime;

import android.inputmethodservice.InputMethodService;
import android.view.View;
import android.view.inputmethod.InputConnection;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.MotionEvent;
import java.util.*;

public class LclImeService extends InputMethodService {
    private Board board;
    @Override public View onCreateInputView() {
        board = new Board(this);
        return board;
    }
    @Override public void onStartInputView(android.view.inputmethod.EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        if (board != null) board.invalidate();
    }
    void commit(String s) {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null && s != null && !s.isEmpty()) ic.commitText(s, 1);
    }
    void composing(String s) {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) ic.setComposingText(s, 1);
    }
    void finishCompose() {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) ic.finishComposingText();
    }
    void backspace() {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) ic.deleteSurroundingText(1, 0);
    }
    void enter() {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) ic.sendKeyEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_ENTER));
        if (ic != null) ic.sendKeyEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_ENTER));
    }

    class Board extends View {
        final Paint p = new Paint(3);
        final String[] modes = {"26键","T9","数字","符号","手写"};
        final String[][] dict = {
            {"你","泥","尼","呢","拟","逆"}, {"好","号","浩","豪","耗"}, {"我","握","窝","喔"},
            {"是","时","事","市","试"}, {"的","得","地"}, {"吗","妈","马","嘛"},
            {"中国","中国人","中华"}, {"你好","您好"}
        };
        final Map<String,String[]> words = new HashMap<>();
        String mode="26键", theme="浅色", lang="中", script="简", composing="";
        boolean shift=false, menu=false, handwriting=false;
        ArrayList<String> candidates = new ArrayList<>();
        ArrayList<Float> strokeX = new ArrayList<>(), strokeY = new ArrayList<>();
        String lastT9="", t9Letters=""; long lastTap=0;
        int bg=0xFFF0F1F4, key=Color.WHITE, ink=0xFF202124, accent=0xFF4C83D8;
        float topH, candH, keyH, bottomH, w, h;
        Board(Context c) {
            super(c);
            words.put("ni",new String[]{"你","泥","尼","呢","拟","逆"});
            words.put("hao",new String[]{"好","号","浩","豪","耗"});
            words.put("wo",new String[]{"我","握","窝","喔"});
            words.put("shi",new String[]{"是","时","事","市","试"});
            words.put("de",new String[]{"的","得","地"});
            words.put("ma",new String[]{"吗","妈","马","嘛"});
            words.put("zhong",new String[]{"中","种","重","众"});
            words.put("guo",new String[]{"国","过","果","锅"});
            words.put("ren",new String[]{"人","仁","认"});
            words.put("tian",new String[]{"天","田","甜"});
            words.put("qi",new String[]{"气","起","其","七"});
            words.put("今天",new String[]{"今天"});
            setLayerType(View.LAYER_TYPE_SOFTWARE,null);
        }
        void colors() {
            if(theme.equals("深色")) { bg=0xFF202124; key=0xFF35363A; ink=Color.WHITE; accent=0xFF8AB4F8; }
            else if(theme.equals("蓝色")) { bg=0xFFE5EFFB; key=Color.WHITE; ink=0xFF17365D; accent=0xFF3478C8; }
            else if(theme.equals("绿色")) { bg=0xFFE5F2E9; key=Color.WHITE; ink=0xFF17452A; accent=0xFF29965A; }
            else { bg=0xFFF0F1F4; key=Color.WHITE; ink=0xFF202124; accent=0xFF4C83D8; }
        }
        protected void onDraw(Canvas c) {
            super.onDraw(c); w=getWidth(); h=getHeight(); colors();
            topH=h*.14f; candH=candidates.isEmpty()?0:h*.13f; bottomH=h*.16f; keyH=(h-topH-candH-bottomH);
            c.drawColor(bg);
            String[] top={"键盘","主题","剪贴板","隐藏"};
            for(int i=0;i<4;i++) box(c,i*w/4,0,(i+1)*w/4,topH, key, top[i], ink, 13);
            if(candH>0) {
                String line = composing.isEmpty() ? "" : composing+"   ";
                drawText(c,line,8,topH+candH*.66f,accent,15,Paint.Align.LEFT);
                float start=Math.max(8,Math.min(w*.32f,line.length()*12+8));
                for(int i=0;i<candidates.size() && i<6;i++) drawText(c,candidates.get(i),start+i*(w-start)/6+12,topH+candH*.66f,ink,17,Paint.Align.CENTER);
            }
            float y=topH+candH;
            if(mode.equals("手写")) drawHand(c,y);
            else drawKeys(c,y);
            String[] bottom={lang,script,shift?"SHIFT↑":"SHIFT","空格","⌫","↵"};
            float[] widths={.12f,.12f,.15f,.27f,.16f,.18f};
            float x=0;
            for(int i=0;i<6;i++){ float ww=w*widths[i]; box(c,x,h-bottomH,x+ww,h, i==3?accent:key,bottom[i],i==3?Color.WHITE:ink,14); x+=ww; }
            if(menu) drawMenu(c);
        }
        void box(Canvas c,float l,float t,float r,float b,int color,String s,int tc,float size) {
            p.setColor(color); p.setStyle(Paint.Style.FILL); c.drawRoundRect(new RectF(l+3,t+3,r-3,b-3),10,10,p);
            drawText(c,s,(l+r)/2,(t+b)/2+size*.35f,tc,size,Paint.Align.CENTER);
        }
        void drawText(Canvas c,String s,float x,float y,int color,float size,Paint.Align a) {
            p.setColor(color); p.setTextSize(size); p.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL)); p.setTextAlign(a); c.drawText(s,x,y,p);
        }
        void drawKeys(Canvas c,float y) {
            String[][] rows;
            if(mode.equals("26键")) rows=new String[][]{{"q","w","e","r","t","y","u","i","o","p"},{"a","s","d","f","g","h","j","k","l"},{"⇧","z","x","c","v","b","n","m","⌫"}};
            else if(mode.equals("T9")) rows=new String[][]{{"1","2","3"},{"4","5","6"},{"7","8","9"},{"*","0","#"}};
            else if(mode.equals("数字")) rows=new String[][]{{"1","2","3"},{"4","5","6"},{"7","8","9"},{"-","0","."}};
            else rows=new String[][]{{"。","，","？","！","；","："},{"、","…","（","）","“","”"},{"《","》","—","￥","@","/"},{"！","？","%","&","*","…"}};
            float rowH=keyH/rows.length;
            for(int r=0;r<rows.length;r++){
                String[] row=rows[r]; float kw=w/row.length;
                for(int i=0;i<row.length;i++){
                    String label=row[i]; if(mode.equals("26键") && label.length()==1 && shift) label=label.toUpperCase(Locale.ROOT);
                    box(c,i*kw,y+r*rowH,(i+1)*kw,y+(r+1)*rowH,key,label,ink,18);
                }
            }
        }
        void drawHand(Canvas c,float y) {
            p.setColor(0xFFB7C0CC); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2);
            c.drawRoundRect(new RectF(8,y+8,w-8,y+keyH-8),12,12,p); p.setStyle(Paint.Style.FILL);
            drawText(c,"在此区域手写 · 松手识别（简易笔画规则）",w/2,y+keyH*.52f,ink,15,Paint.Align.CENTER);
            box(c,w-90,y+keyH-48,w-10,y+keyH-8,key,"清空",ink,13);
        }
        void drawMenu(Canvas c) {
            String[] opts=modes; float mw=w*.42f, mh=keyH*.58f;
            p.setColor(theme.equals("深色")?0xFF45464A:Color.WHITE); c.drawRoundRect(new RectF(8,topH+4,mw,topH+mh),12,12,p);
            for(int i=0;i<opts.length;i++) drawText(c,opts[i],mw/2,topH+28+i*(mh/5)+12,ink,15,Paint.Align.CENTER);
        }
        void refreshCandidates() {
            candidates.clear();
            if(!composing.isEmpty()) {
                String py=composing.toLowerCase(Locale.ROOT);
                if(words.containsKey(py)) candidates.addAll(Arrays.asList(words.get(py)));
                else if(py.matches("[a-z]+")) { candidates.add(py); }
            }
            invalidate();
        }
        void keyInput(String s) {
            if(lang.equals("En")) { commit(shift?s.toUpperCase(Locale.ROOT):s); if(shift) shift=false; return; }
            if(mode.equals("T9")) {
                String[] groups={"","", "abc","def","ghi","jkl","mno","pqrs","tuv","wxyz"};
                if(s.matches("[2-9]")) {
                    long now=System.currentTimeMillis();
                    if(lastT9.equals(s) && now-lastTap<850) { t9Letters=groups[Integer.parseInt(s)].substring((groups[Integer.parseInt(s)].indexOf(t9Letters)+1)%groups[Integer.parseInt(s)].length(),(groups[Integer.parseInt(s)].indexOf(t9Letters)+1)%groups[Integer.parseInt(s)].length()+1); }
                    else t9Letters=groups[Integer.parseInt(s)].substring(0,1);
                    lastT9=s; lastTap=now; composing+=t9Letters; composing(composing); refreshCandidates(); return;
                }
            }
            if(s.length()==1 && s.matches("[a-zA-Z]")) { composing+=s.toLowerCase(Locale.ROOT); composing(composing); refreshCandidates(); }
            else commit(s);
        }
        void choose(int i) {
            if(i>=0 && i<candidates.size()) {
                String s=candidates.get(i);
                if(script.equals("繁")) s=toTraditional(s);
                finishCompose(); commit(s); composing=""; candidates.clear(); invalidate();
            }
        }
        String toTraditional(String s) {
            String a="汉语龙门国马后发里云万与这来为"; String b="漢語龍門國馬後發裡雲萬與這來為";
            StringBuilder out=new StringBuilder(); for(char ch:s.toCharArray()){int n=a.indexOf(ch);out.append(n>=0?b.charAt(n):ch);} return out.toString();
        }
        public boolean onTouchEvent(MotionEvent e) {
            float x=e.getX(), y=e.getY();
            if(mode.equals("手写") && y>topH+candH && y<h-bottomH) {
                if(e.getAction()==MotionEvent.ACTION_DOWN){strokeX.clear();strokeY.clear(); strokeX.add(x);strokeY.add(y); return true;}
                if(e.getAction()==MotionEvent.ACTION_MOVE){strokeX.add(x);strokeY.add(y); invalidate(); return true;}
                if(e.getAction()==MotionEvent.ACTION_UP){
                    if(x>w-100 && y>h-bottomH-60){strokeX.clear();strokeY.clear();return true;}
                    int n=strokeX.size(); String[] guess=n<8?new String[]{"一","丨","丿","丶"}:new String[]{"口","人","十","大"};
                    candidates.clear(); candidates.addAll(Arrays.asList(guess)); invalidate(); return true;
                }
            }
            if(e.getAction()!=MotionEvent.ACTION_UP) return true;
            if(y<topH) {
                int i=(int)(x/(w/4));
                if(i==0) {menu=!menu;}
                if(i==1) {theme=theme.equals("浅色")?"深色":theme.equals("深色")?"蓝色":theme.equals("蓝色")?"绿色":"浅色";}
                if(i==2) {ClipboardManager cm=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE); if(cm!=null&&cm.hasPrimaryClip()){ClipData d=cm.getPrimaryClip();if(d!=null&&d.getItemCount()>0){CharSequence cs=d.getItemAt(0).coerceToText(getContext());if(cs!=null)commit(cs.toString());}}}
                if(i==3) requestHideSelf(0); invalidate(); return true;
            }
            if(menu) {
                if(y<topH+keyH*.58f) {int i=Math.min(4,Math.max(0,(int)((y-topH)/(keyH*.58f/5)))); mode=modes[i]; menu=false; composing="";candidates.clear();finishCompose();}
                else menu=false; invalidate();return true;
            }
            if(candH>0 && y>=topH && y<topH+candH) {
                float start=w*.30f;
                int idx=(int)((x-start)/((w-start)/6f));
                if(x>=start) choose(Math.max(0,Math.min(candidates.size()-1,idx)));
                return true;
            }
            if(y>=h-bottomH) {
                float[] cuts={.12f,.24f,.39f,.66f,.82f,1f}; int i=0;while(i<5&&x>w*cuts[i])i++;
                if(i==0){lang=lang.equals("中")?"En":"中";composing="";candidates.clear();finishCompose();}
                else if(i==1) script=script.equals("简")?"繁":"简";
                else if(i==2) shift=!shift;
                else if(i==3){if(lang.equals("中")&&!composing.isEmpty()&&!candidates.isEmpty())choose(0);else commit(" ");}
                else if(i==4){if(!composing.isEmpty()){composing=composing.substring(0,composing.length()-1);composing(composing);refreshCandidates();}else backspace();}
                else enter();
                invalidate();return true;
            }
            if(y>=topH+candH && y<h-bottomH) {
                if(y<topH+candH+keyH*.22f && !candidates.isEmpty()){int idx=Math.max(0,Math.min(candidates.size()-1,(int)(x/(w/Math.max(1,candidates.size())))));choose(idx);return true;}
                float ky=y-(topH+candH);
                if(mode.equals("26键")) {
                    String[][] rows={{"q","w","e","r","t","y","u","i","o","p"},{"a","s","d","f","g","h","j","k","l"},{"z","x","c","v","b","n","m"}};
                    int row=Math.min(2,(int)(ky/(keyH/3))); int col=Math.min(rows[row].length-1,(int)(x/(w/rows[row].length)));
                    String s=rows[row][col]; if(shift)s=s.toUpperCase(Locale.ROOT); keyInput(s); if(shift)shift=false;
                } else {
                    String[][] rows=mode.equals("T9")?new String[][]{{"1","2","3"},{"4","5","6"},{"7","8","9"},{"*","0","#"}}:mode.equals("数字")?new String[][]{{"1","2","3"},{"4","5","6"},{"7","8","9"},{"-","0","."}}:new String[][]{{"。","，","？","！","；","："},{"、","…","（","）","“","”"},{"《","》","—","￥","@","/"},{"！","？","%","&","*","…"}};
                    int row=Math.min(rows.length-1,(int)(ky/(keyH/rows.length)));int col=Math.min(rows[row].length-1,(int)(x/(w/rows[row].length)));keyInput(rows[row][col]);
                }
                invalidate();return true;
            }
            return true;
        }
    }
}
