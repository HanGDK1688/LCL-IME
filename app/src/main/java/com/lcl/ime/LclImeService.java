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
import android.text.Editable;
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
        @Override
        public void run() {
            if (board != null) {
                board.handleLongDelete();
            }
        }
    };

    @Override
    public View onCreateInputView() {
        board = new Board(this);
        return board;
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        if (board != null) {
            board.invalidate();
        }
    }

    void commitText(String text) {
        if (text == null || text.isEmpty()) return;
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            ic.commitText(text, 1);
        }
    }

    void setComposingText(String text) {
        if (text == null || text.isEmpty()) {
            finishComposingText();
            return;
        }
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            ic.setComposingText(text, 1);
        }
    }

    void finishComposingText() {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            ic.finishComposingText();
        }
    }

    void deleteOneChar() {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            ic.deleteSurroundingText(1, 0);
        }
    }

    void clearCurrentText() {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            Editable editable = ic.getEditableText();
            if (editable != null) {
                editable.clear();
            }
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

        private String currentMode = "26键";
        private String theme = "浅色";
        private String language = "中";
        private String script = "简";
        private boolean shift = false;
        private boolean menuOpen = false;
        private String composing = "";
        private String t9Buffer = "";
        private String lastT9Key = "";
        private long lastT9TapAt = 0L;
        private ArrayList<String> candidates = new ArrayList<>();
        private ArrayList<Float> strokeX = new ArrayList<>();
        private ArrayList<Float> strokeY = new ArrayList<>();

        private int bgColor;
        private int keyColor;
        private int textColor;
        private int accentColor;
        private int accentTextColor;

        private float width;
        private float height;
        private float topBarHeight;
        private float bottomBarHeight;
        private float candidateHeight;
        private float keyboardHeight;

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
            traditionalMap.put('国', '國');
            traditionalMap.put('人', '人');
            traditionalMap.put('我', '我');
            traditionalMap.put('你', '你');
            traditionalMap.put('好', '好');
            traditionalMap.put('中', '中');
            traditionalMap.put('来', '來');
            traditionalMap.put('为', '為');
            traditionalMap.put('与', '與');
            traditionalMap.put('这', '這');
            traditionalMap.put('里', '裡');
            traditionalMap.put('云', '雲');
            traditionalMap.put('万', '萬');
            traditionalMap.put('汉', '漢');
            traditionalMap.put('语', '語');
            traditionalMap.put('龙', '龍');
            traditionalMap.put('门', '門');
            traditionalMap.put('马', '馬');
            traditionalMap.put('后', '後');
            traditionalMap.put('发', '發');
        }

        private void updateThemeColors() {
            if ("深色".equals(theme)) {
                bgColor = 0xFF202124;
                keyColor = 0xFF2E3136;
                textColor = 0xFFFFFFFF;
                accentColor = 0xFF8AB4F8;
                accentTextColor = 0xFFFFFFFF;
            } else if ("蓝色".equals(theme)) {
                bgColor = 0xFFEAF3FF;
                keyColor = 0xFFFFFFFF;
                textColor = 0xFF183A5B;
                accentColor = 0xFF3A7FE8;
                accentTextColor = 0xFFFFFFFF;
            } else if ("绿色".equals(theme)) {
                bgColor = 0xFFEAF7EE;
                keyColor = 0xFFFFFFFF;
                textColor = 0xFF18472D;
                accentColor = 0xFF2E9C5A;
                accentTextColor = 0xFFFFFFFF;
            } else {
                bgColor = 0xFFF2F3F5;
                keyColor = 0xFFFFFFFF;
                textColor = 0xFF1F2328;
                accentColor = 0xFF4C83D8;
                accentTextColor = 0xFFFFFFFF;
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            width = getWidth();
            height = getHeight();
            updateThemeColors();

            topBarHeight = height * 0.14f;
            candidateHeight = candidates.isEmpty() ? 0f : height * 0.12f;
            bottomBarHeight = height * 0.16f;
            keyboardHeight = height - topBarHeight - candidateHeight - bottomBarHeight;

            canvas.drawColor(bgColor);
            drawTopBar(canvas);
            drawCandidateBar(canvas);
            drawMainArea(canvas);
            drawBottomBar(canvas);
            if (menuOpen) {
                drawModeMenu(canvas);
            }
        }

        private void drawTopBar(Canvas canvas) {
            float cellWidth = width / 4f;
            String[] labels = {"键盘", "主题", "剪贴板", "隐藏"};
            for (int i = 0; i < 4; i++) {
                float left = i * cellWidth + 4f;
                float top = 8f;
                float right = (i + 1) * cellWidth - 4f;
                float bottom = topBarHeight - 8f;
                drawRoundedRect(canvas, left, top, right, bottom, keyColor);
                drawText(canvas, labels[i], (left + right) / 2f, topBarHeight * 0.58f, textColor, 16f, Paint.Align.CENTER);
            }
        }

        private void drawCandidateBar(Canvas canvas) {
            if (candidates.isEmpty()) return;
            float y = topBarHeight + candidateHeight * 0.58f;
            if (!composing.isEmpty()) {
                drawText(canvas, composing + " ", 12f, y, accentColor, 16f, Paint.Align.LEFT);
            }
            float startX = Math.max(90f, width * 0.32f);
            float slotWidth = (width - startX) / Math.max(1, Math.min(6, candidates.size()));
            for (int i = 0; i < Math.min(candidates.size(), 6); i++) {
                drawText(canvas, candidates.get(i), startX + i * slotWidth + slotWidth * 0.5f, y, textColor, 17f, Paint.Align.CENTER);
            }
        }

        private void drawMainArea(Canvas canvas) {
            float yStart = topBarHeight + candidateHeight;
            if ("手写".equals(currentMode)) {
                drawHandwriteArea(canvas, yStart);
            } else {
                drawKeyLayout(canvas, yStart);
            }
        }

        private void drawKeyLayout(Canvas canvas, float yStart) {
            String[][] rows;
            if ("26键".equals(currentMode)) {
                rows = new String[][]{{"q", "w", "e", "r", "t", "y", "u", "i", "o", "p"},
                        {"a", "s", "d", "f", "g", "h", "j", "k", "l"},
                        {"z", "x", "c", "v", "b", "n", "m"}};
            } else if ("T9".equals(currentMode)) {
                rows = new String[][]{{"1", "2", "3"}, {"4", "5", "6"}, {"7", "8", "9"}, {"*", "0", "#"}};
            } else if ("数字".equals(currentMode)) {
                rows = new String[][]{{"1", "2", "3"}, {"4", "5", "6"}, {"7", "8", "9"}, {"-", "0", "."}};
            } else {
                rows = new String[][]{{"。", "，", "？", "！", "；", "："},
                        {"、", "…", "（", "）", "“", "”"},
                        {"《", "》", "—", "￥", "@", "/"},
                        {"！", "？", "%", "&", "*", "…"}};
            }

            float keyTop = yStart;
            float rowHeight = keyboardHeight / rows.length;
            for (int rowIndex = 0; rowIndex < rows.length; rowIndex++) {
                String[] row = rows[rowIndex];
                float currentY = keyTop + rowIndex * rowHeight;
                float cellWidth = width / row.length;
                for (int col = 0; col < row.length; col++) {
                    float left = col * cellWidth + 4f;
                    float right = (col + 1) * cellWidth - 4f;
                    float top = currentY + 4f;
                    float bottom = currentY + rowHeight - 6f;
                    String label = row[col];
                    if ("26键".equals(currentMode) && label.length() == 1 && Character.isLetter(label.charAt(0))) {
                        label = shift ? label.toUpperCase(Locale.ROOT) : label.toLowerCase(Locale.ROOT);
                    }
                    drawRoundedRect(canvas, left, top, right, bottom, keyColor);
                    drawText(canvas, label, (left + right) / 2f, (top + bottom) / 2f + 10f, textColor, 18f, Paint.Align.CENTER);
                }
            }
        }

        private void drawHandwriteArea(Canvas canvas, float yStart) {
            float boardTop = yStart + 8f;
            float boardBottom = height - bottomBarHeight - 8f;
            drawRoundedRect(canvas, 8f, boardTop, width - 8f, boardBottom, 0xFFCED4DC);
            drawText(canvas, "手写区：画字后松手识别", width / 2f, (boardTop + boardBottom) / 2f, textColor, 15f, Paint.Align.CENTER);

            float clearLeft = width - 110f;
            float clearTop = boardBottom - 54f;
            float clearRight = width - 12f;
            float clearBottom = boardBottom - 12f;
            drawRoundedRect(canvas, clearLeft, clearTop, clearRight, clearBottom, keyColor);
            drawText(canvas, "清空", (clearLeft + clearRight) / 2f, clearBottom - 22f, textColor, 14f, Paint.Align.CENTER);

            if (!strokeX.isEmpty() && strokeY.size() == strokeX.size()) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setColor(accentColor);
                paint.setStrokeWidth(4f);
                for (int i = 1; i < strokeX.size(); i++) {
                    canvas.drawLine(strokeX.get(i - 1), strokeY.get(i - 1), strokeX.get(i), strokeY.get(i), paint);
                }
                paint.setStyle(Paint.Style.FILL);
            }
        }

        private void drawBottomBar(Canvas canvas) {
            String[] labels = {language, script, shift ? "SHIFT↑" : "SHIFT", "空格", "⌫", "↵"};
            float[] widths = {0.12f, 0.12f, 0.15f, 0.27f, 0.14f, 0.20f};
            float x = 0f;
            for (int i = 0; i < labels.length; i++) {
                float cellWidth = width * widths[i];
                float left = x + 2f;
                float top = height - bottomBarHeight + 4f;
                float right = x + cellWidth - 2f;
                float bottom = height - 4f;
                int fillColor = (i == 3) ? accentColor : keyColor;
                int textTint = (i == 3) ? accentTextColor : textColor;
                drawRoundedRect(canvas, left, top, right, bottom, fillColor);
                drawText(canvas, labels[i], (left + right) / 2f, (top + bottom) / 2f + 8f, textTint, 17f, Paint.Align.CENTER);
                x += cellWidth;
            }
        }

        private void drawModeMenu(Canvas canvas) {
            float menuX = 10f;
            float menuY = topBarHeight + 8f;
            float menuW = width * 0.42f;
            float menuH = keyboardHeight * 0.62f;
            drawRoundedRect(canvas, menuX, menuY, menuX + menuW, menuY + menuH, Color.WHITE);
            for (int i = 0; i < modeLabels.length; i++) {
                float y = menuY + 22f + i * (menuH / modeLabels.length);
                drawText(canvas, modeLabels[i], menuX + menuW / 2f, y, textColor, 16f, Paint.Align.CENTER);
            }
        }

        private void drawRoundedRect(Canvas canvas, float left, float top, float right, float bottom, int color) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(color);
            canvas.drawRoundRect(new RectF(left, top, right, bottom), 12f, 12f, paint);
        }

        private void drawText(Canvas canvas, String text, float x, float y, int color, float textSize, Paint.Align align) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(color);
            paint.setTextAlign(align);
            paint.setTextSize(textSize);
            paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            canvas.drawText(text, x, y, paint);
        }

        private void refreshCandidates() {
            candidates.clear();
            if (composing == null || composing.isEmpty()) {
                invalidate();
                return;
            }
            String key = composing.toLowerCase(Locale.ROOT);
            String[] matched = dictionary.get(key);
            if (matched != null) {
                candidates.addAll(Arrays.asList(matched));
            } else if (key.matches("[a-z]+")) {
                candidates.add(key);
            }
            invalidate();
        }

        private void submitCandidate(int index) {
            if (index < 0 || index >= candidates.size()) return;
            String value = candidates.get(index);
            if ("繁".equals(script)) {
                value = toTraditional(value);
            }
            finishComposingText();
            commitText(value);
            composing = "";
            t9Buffer = "";
            lastT9Key = "";
            candidates.clear();
            invalidate();
        }

        private String toTraditional(String value) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < value.length(); i++) {
                char ch = value.charAt(i);
                Character mapped = traditionalMap.get(ch);
                sb.append(mapped == null ? ch : mapped);
            }
            return sb.toString();
        }

        private void addLetterToComposing(String letter) {
            if ("En".equals(language)) {
                String output = shift ? letter.toUpperCase(Locale.ROOT) : letter.toLowerCase(Locale.ROOT);
                commitText(output);
                shift = false;
                invalidate();
                return;
            }
            if ("26键".equals(currentMode) || "T9".equals(currentMode)) {
                if ("T9".equals(currentMode)) {
                    addT9Letter(letter);
                    return;
                }
                composing += letter.toLowerCase(Locale.ROOT);
                setComposingText(composing);
                refreshCandidates();
            } else {
                commitText(letter);
            }
        }

        private void addT9Letter(String digit) {
            if (!digit.matches("[2-9]")) return;
            final String[] groups = {"", "", "abc", "def", "ghi", "jkl", "mno", "pqrs", "tuv", "wxyz"};
            String group = groups[Integer.parseInt(digit)];
            if (group.isEmpty()) return;

            long now = System.currentTimeMillis();
            if (lastT9Key.equals(digit) && now - lastT9TapAt < 800 && !t9Buffer.isEmpty()) {
                int currentIndex = group.indexOf(t9Buffer.charAt(t9Buffer.length() - 1));
                int nextIndex = (currentIndex + 1) % group.length();
                t9Buffer = String.valueOf(group.charAt(nextIndex));
            } else {
                t9Buffer = String.valueOf(group.charAt(0));
            }
            lastT9Key = digit;
            lastT9TapAt = now;
            composing += t9Buffer;
            setComposingText(composing);
            refreshCandidates();
        }

        private void handleTopBarTap(int index) {
            if (index == 0) {
                menuOpen = !menuOpen;
            } else if (index == 1) {
                if ("浅色".equals(theme)) {
                    theme = "深色";
                } else if ("深色".equals(theme)) {
                    theme = "蓝色";
                } else if ("蓝色".equals(theme)) {
                    theme = "绿色";
                } else {
                    theme = "浅色";
                }
            } else if (index == 2) {
                ClipboardManager cm = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null && cm.hasPrimaryClip()) {
                    ClipData clip = cm.getPrimaryClip();
                    if (clip != null && clip.getItemCount() > 0) {
                        CharSequence text = clip.getItemAt(0).coerceToText(getContext());
                        if (text != null) {
                            commitText(text.toString());
                        }
                    }
                }
            } else if (index == 3) {
                requestHideSelf(0);
            }
            invalidate();
        }

        private void handleModeChoice(int index) {
            if (index < 0 || index >= modeLabels.length) return;
            currentMode = modeLabels[index];
            menuOpen = false;
            composing = "";
            t9Buffer = "";
            lastT9Key = "";
            candidates.clear();
            finishComposingText();
            invalidate();
        }

        private void handleBottomAction(int index) {
            if (index == 0) {
                language = "中".equals(language) ? "En" : "中";
                composing = "";
                t9Buffer = "";
                lastT9Key = "";
                candidates.clear();
                finishComposingText();
            } else if (index == 1) {
                script = "简".equals(script) ? "繁" : "简";
                if (!candidates.isEmpty()) {
                    refreshCandidates();
                }
            } else if (index == 2) {
                shift = !shift;
            } else if (index == 3) {
                if ("中".equals(language) && !composing.isEmpty() && !candidates.isEmpty()) {
                    submitCandidate(0);
                } else {
                    commitText(" ");
                }
            } else if (index == 4) {
                if (!composing.isEmpty()) {
                    composing = composing.substring(0, composing.length() - 1);
                    setComposingText(composing);
                    refreshCandidates();
                } else {
                    deleteOneChar();
                }
            } else if (index == 5) {
                sendEnter();
            }
            invalidate();
        }

        public void handleLongDelete() {
            composing = "";
            t9Buffer = "";
            lastT9Key = "";
            candidates.clear();
            finishComposingText();
            clearCurrentText();
            invalidate();
        }

        private int bottomIndexForX(float x) {
            float[] cuts = {0.12f, 0.24f, 0.39f, 0.66f, 0.82f, 1.0f};
            for (int i = 0; i < cuts.length; i++) {
                if (x <= width * cuts[i]) {
                    return i;
                }
            }
            return 5;
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            float x = event.getX();
            float y = event.getY();

            if (event.getAction() == MotionEvent.ACTION_DOWN && y >= height - bottomBarHeight && y <= height && x >= width * 0.82f && x <= width * 0.96f) {
                uiHandler.removeCallbacks(longDeleteRunnable);
                uiHandler.postDelayed(longDeleteRunnable, 500L);
            }
            if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                uiHandler.removeCallbacks(longDeleteRunnable);
            }

            if (event.getAction() != MotionEvent.ACTION_UP) {
                if ("手写".equals(currentMode) && y > topBarHeight + candidateHeight && y < height - bottomBarHeight) {
                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        strokeX.clear();
                        strokeY.clear();
                    }
                    if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_MOVE) {
                        strokeX.add(x);
                        strokeY.add(y);
                        invalidate();
                        return true;
                    }
                }
                return true;
            }

            if (y < topBarHeight) {
                int index = (int) (x / (width / 4f));
                if (index >= 0 && index <= 3) {
                    handleTopBarTap(index);
                }
                return true;
            }

            if (menuOpen && y >= topBarHeight && y <= topBarHeight + keyboardHeight * 0.62f) {
                float menuTop = topBarHeight + 8f;
                float menuHeight = keyboardHeight * 0.62f;
                int index = (int) ((y - menuTop) / (menuHeight / modeLabels.length));
                handleModeChoice(index);
                return true;
            }

            if (!candidates.isEmpty() && y >= topBarHeight && y < topBarHeight + candidateHeight) {
                float startX = Math.max(90f, width * 0.32f);
                int index = (int) ((x - startX) / ((width - startX) / Math.max(1, Math.min(6, candidates.size()))));
                submitCandidate(Math.max(0, Math.min(candidates.size() - 1, index)));
                return true;
            }

            if (y >= height - bottomBarHeight) {
                handleBottomAction(bottomIndexForX(x));
                return true;
            }

            if ("手写".equals(currentMode) && y > topBarHeight + candidateHeight && y < height - bottomBarHeight) {
                if (x > width - 110f && y > height - bottomBarHeight - 54f) {
                    strokeX.clear();
                    strokeY.clear();
                    candidates.clear();
                    invalidate();
                    return true;
                }
                if (strokeX.size() > 8) {
                    candidates.clear();
                    candidates.add("一");
                    candidates.add("二");
                    candidates.add("口");
                    candidates.add("人");
                    candidates.add("大");
                    candidates.add("中");
                    invalidate();
                }
                return true;
            }

            if (y > topBarHeight + candidateHeight && y < height - bottomBarHeight) {
                if ("26键".equals(currentMode)) {
                    String[][] rows = {{"q", "w", "e", "r", "t", "y", "u", "i", "o", "p"},
                            {"a", "s", "d", "f", "g", "h", "j", "k", "l"},
                            {"z", "x", "c", "v", "b", "n", "m"}};
                    int row = (int) ((y - (topBarHeight + candidateHeight)) / (keyboardHeight / rows.length));
                    row = Math.max(0, Math.min(rows.length - 1, row));
                    float cellWidth = width / rows[row].length;
                    int col = (int) (x / cellWidth);
                    col = Math.max(0, Math.min(rows[row].length - 1, col));
                    addLetterToComposing(rows[row][col]);
                    return true;
                }

                if ("T9".equals(currentMode)) {
                    String[][] rows = {{"1", "2", "3"}, {"4", "5", "6"}, {"7", "8", "9"}, {"*", "0", "#"}};
                    int row = (int) ((y - (topBarHeight + candidateHeight)) / (keyboardHeight / rows.length));
                    row = Math.max(0, Math.min(rows.length - 1, row));
                    float cellWidth = width / rows[row].length;
                    int col = (int) (x / cellWidth);
                    col = Math.max(0, Math.min(rows[row].length - 1, col));
                    addLetterToComposing(rows[row][col]);
                    return true;
                }

                if ("数字".equals(currentMode)) {
                    String[][] rows = {{"1", "2", "3"}, {"4", "5", "6"}, {"7", "8", "9"}, {"-", "0", "."}};
                    int row = (int) ((y - (topBarHeight + candidateHeight)) / (keyboardHeight / rows.length));
                    row = Math.max(0, Math.min(rows.length - 1, row));
                    float cellWidth = width / rows[row].length;
                    int col = (int) (x / cellWidth);
                    col = Math.max(0, Math.min(rows[row].length - 1, col));
                    commitText(rows[row][col]);
                    return true;
                }

                if ("符号".equals(currentMode)) {
                    String[][] rows = {{"。", "，", "？", "！", "；", "："},
                            {"、", "…", "（", "）", "“", "”"},
                            {"《", "》", "—", "￥", "@", "/"},
                            {"！", "？", "%", "&", "*", "…"}};
                    int row = (int) ((y - (topBarHeight + candidateHeight)) / (keyboardHeight / rows.length));
                    row = Math.max(0, Math.min(rows.length - 1, row));
                    float cellWidth = width / rows[row].length;
                    int col = (int) (x / cellWidth);
                    col = Math.max(0, Math.min(rows[row].length - 1, col));
                    commitText(rows[row][col]);
                    return true;
                }
            }
            return true;
        }
    }
}
