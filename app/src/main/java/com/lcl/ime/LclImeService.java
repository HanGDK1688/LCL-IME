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
    private final Runnable deleteLongPressRunnable = new Runnable() {
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
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        if (text == null || text.isEmpty()) {
            ic.finishComposingText();
        } else {
            ic.setComposingText(text, 1);
        }
    }

    void finishComposingText() {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            ic.finishComposingText();
        }
    }

    void deleteSurroundingText() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        ic.deleteSurroundingText(1, 0);
    }

    void deleteAllCurrentText() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        Editable editable = ic.getEditableText();
        if (editable != null) {
            editable.clear();
        }
    }

    void sendEnter() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        ic.sendKeyEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_ENTER));
        ic.sendKeyEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_ENTER));
    }

    class Board extends View {
        private static final float TOP_BAR_RATIO = 0.14f;
        private static final float BOTTOM_BAR_RATIO = 0.16f;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final String[] modeLabels = {"26键", "T9", "数字", "符号", "手写"};
        private final HashMap<String, String[]> pinyinMap = new HashMap<>();
        private final HashMap<Character, Character> traditionalMap = new HashMap<>();
        private ArrayList<String> candidates = new ArrayList<>();
        private ArrayList<Float> strokeX = new ArrayList<>();
        private ArrayList<Float> strokeY = new ArrayList<>();

        private String currentMode = "26键";
        private String theme = "浅色";
        private String language = "中";
        private String script = "简";
        private boolean menuOpen = false;
        private boolean shift = false;
        private String composing = "";
        private String t9Buffer = "";
        private String lastT9Key = "";
        private long lastT9TapAt = 0L;

        private float width;
        private float height;
        private float topHeight;
        private float candHeight;
        private float keyHeight;
        private float bottomHeight;

        private int backgroundColor;
        private int keyColor;
        private int textColor;
        private int accentColor;
        private int accentTextColor;

        Board(Context context) {
            super(context);
            initDictionary();
            initTraditionalMap();
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }

        private void initDictionary() {
            pinyinMap.put("ni", new String[]{"你", "泥", "尼", "呢", "拟", "逆", "妮"});
            pinyinMap.put("hao", new String[]{"好", "号", "浩", "豪", "耗", "毫"});
            pinyinMap.put("wo", new String[]{"我", "握", "窝", "喔", "沃"});
            pinyinMap.put("shi", new String[]{"是", "时", "事", "市", "试", "识"});
            pinyinMap.put("de", new String[]{"的", "得", "地", "德", "底"});
            pinyinMap.put("ma", new String[]{"吗", "妈", "马", "嘛", "麻"});
            pinyinMap.put("zhong", new String[]{"中", "钟", "众", "重", "终"});
            pinyinMap.put("guo", new String[]{"国", "过", "果", "锅", "郭"});
            pinyinMap.put("ren", new String[]{"人", "认", "忍", "仁"});
            pinyinMap.put("tian", new String[]{"天", "田", "甜", "填", "舔"});
            pinyinMap.put("qi", new String[]{"起", "其", "气", "七", "期"});
            pinyinMap.put("xie", new String[]{"写", "些", "谢", "卸", "斜"});
            pinyinMap.put("yi", new String[]{"一", "以", "意", "已", "易"});
            pinyinMap.put("er", new String[]{"二", "而", "儿", "额", "耳"});
            pinyinMap.put("ri", new String[]{"日", "日子", "认"});
            pinyinMap.put("zhongguo", new String[]{"中国", "中国人", "中华"});
            pinyinMap.put("nihao", new String[]{"你好", "您好", "你好吗"});
            pinyinMap.put("ming", new String[]{"明", "名", "命", "鸣"});
            pinyinMap.put("zhongguoren", new String[]{"中国人"});
        }

        private void initTraditionalMap() {
            traditionalMap.put('中', '中');
            traditionalMap.put('国', '國');
            traditionalMap.put('人', '人');
            traditionalMap.put('我', '我');
            traditionalMap.put('你', '你');
            traditionalMap.put('好', '好');
            traditionalMap.put('天', '天');
            traditionalMap.put('明', '明');
            traditionalMap.put('日', '日');
            traditionalMap.put('一', '一');
            traditionalMap.put('二', '二');
            traditionalMap.put('三', '三');
            traditionalMap.put('四', '四');
            traditionalMap.put('五', '五');
            traditionalMap.put('六', '六');
            traditionalMap.put('七', '七');
            traditionalMap.put('八', '八');
            traditionalMap.put('九', '九');
            traditionalMap.put('零', '零');
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

        private void applyTheme() {
            if ("深色".equals(theme)) {
                backgroundColor = 0xFF202124;
                keyColor = 0xFF2E3136;
                textColor = 0xFFFFFFFF;
                accentColor = 0xFF8AB4F8;
                accentTextColor = 0xFFFFFFFF;
            } else if ("蓝色".equals(theme)) {
                backgroundColor = 0xFFEAF3FF;
                keyColor = 0xFFFFFFFF;
                textColor = 0xFF183A5B;
                accentColor = 0xFF3A7FE8;
                accentTextColor = 0xFFFFFFFF;
            } else if ("绿色".equals(theme)) {
                backgroundColor = 0xFFEAF7EE;
                keyColor = 0xFFFFFFFF;
                textColor = 0xFF18472D;
                accentColor = 0xFF2E9C5A;
                accentTextColor = 0xFFFFFFFF;
            } else {
                backgroundColor = 0xFFF2F3F5;
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
            applyTheme();

            topHeight = height * TOP_BAR_RATIO;
            candHeight = candidates.isEmpty() ? 0 : height * 0.12f;
            bottomHeight = height * BOTTOM_BAR_RATIO;
            keyHeight = height - topHeight - candHeight - bottomHeight;

            paint.setStyle(Paint.Style.FILL);
            canvas.drawColor(backgroundColor);

            drawTopBar(canvas);
            drawCandBar(canvas);
            drawKeyboardArea(canvas);
            drawBottomBar(canvas);
            if (menuOpen) {
                drawModeMenu(canvas);
            }
        }

        private void drawTopBar(Canvas canvas) {
            float cell = width / 4f;
            String[] labels = {"键盘", "主题", "剪贴板", "隐藏"};
            for (int i = 0; i < 4; i++) {
                float left = i * cell;
                float right = left + cell;
                drawRoundedRect(canvas, left + 4, 8, right - 4, topHeight - 8, keyColor);
                drawText(canvas, labels[i], (left + right) / 2f, topHeight * 0.58f, textColor, 16f, Paint.Align.CENTER);
            }
        }

        private void drawCandBar(Canvas canvas) {
            if (candidates.isEmpty()) return;
            float y = topHeight + candHeight * 0.58f;
            String prefix = composing.isEmpty() ? "" : composing + " ";
            drawText(canvas, prefix, 12, y, accentColor, 16f, Paint.Align.LEFT);

            float startX = Math.max(90f, width * 0.32f);
            float slot = (width - startX) / Math.max(1, Math.min(6, candidates.size()));
            for (int i = 0; i < Math.min(candidates.size(), 6); i++) {
                drawText(canvas, candidates.get(i), startX + i * slot + slot * 0.5f, y, textColor, 17f, Paint.Align.CENTER);
            }
        }

        private void drawKeyboardArea(Canvas canvas) {
            float yStart = topHeight + candHeight;
            if ("手写".equals(currentMode)) {
                drawHandWriteArea(canvas, yStart);
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

            float rowGap = 8f;
            float rowHeight = (height - yStart - bottomHeight) / rows.length;
            for (int row = 0; row < rows.length; row++) {
                float currentY = yStart + row * rowHeight;
                String[] cells = rows[row];
                float cellWidth = width / cells.length;
                for (int col = 0; col < cells.length; col++) {
                    float left = col * cellWidth + 4;
                    float top = currentY + 4;
                    float right = left + cellWidth - 8;
                    float bottom = currentY + rowHeight - 6;
                    String label = cells[col];
                    if ("26键".equals(currentMode) && label.length() == 1 && Character.isLetter(label.charAt(0))) {
                        label = shift ? label.toUpperCase(Locale.ROOT) : label.toLowerCase(Locale.ROOT);
                    }
                    drawRoundedRect(canvas, left, top, right, bottom, keyColor);
                    drawText(canvas, label, (left + right) / 2f, (top + bottom) / 2f + 10f, textColor, 18f, Paint.Align.CENTER);
                }
            }
        }

        private void drawHandWriteArea(Canvas canvas, float yStart) {
            float boardTop = yStart + 8f;
            float boardBottom = height - bottomHeight - 8f;
            drawRoundedRect(canvas, 8f, boardTop, width - 8f, boardBottom, 0xFFCDD3DB);
            drawText(canvas, "手写区：画字后松手识别", width / 2f, (boardTop + boardBottom) / 2f, textColor, 15f, Paint.Align.CENTER);
            float clearLeft = width - 110f;
            float clearTop = boardBottom - 54f;
            drawRoundedRect(canvas, clearLeft, clearTop, width - 12f, boardBottom - 12f, keyColor);
            drawText(canvas, "清空", (clearLeft + width - 12f) / 2f, boardBottom - 32f, textColor, 14f, Paint.Align.CENTER);
            if (!strokeX.isEmpty()) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setColor(accentColor);
                paint.setStrokeWidth(4f);
                for (int i = 1; i < strokeX.size(); i++) {
                    float x1 = strokeX.get(i - 1);
                    float y1 = strokeY.get(i - 1);
                    float x2 = strokeX.get(i);
                    float y2 = strokeY.get(i);
                    canvas.drawLine(x1, y1, x2, y2, paint);
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
                float left = x;
                float top = height - bottomHeight;
                float right = left + cellWidth;
                float bottom = height;
                if (i == 3) {
                    drawRoundedRect(canvas, left + 2f, top + 4f, right - 2f, bottom - 4f, accentColor);
                    drawText(canvas, labels[i], (left + right) / 2f, height - bottomHeight * 0.52f, accentTextColor, 19f, Paint.Align.CENTER);
                } else {
                    drawRoundedRect(canvas, left + 2f, top + 4f, right - 2f, bottom - 4f, keyColor);
                    drawText(canvas, labels[i], (left + right) / 2f, height - bottomHeight * 0.52f, textColor, 17f, Paint.Align.CENTER);
                }
                x += cellWidth;
            }
        }

        private void drawModeMenu(Canvas canvas) {
            float menuX = 10f;
            float menuY = topHeight + 8f;
            float menuW = width * 0.42f;
            float menuH = keyHeight * 0.62f;
            drawRoundedRect(canvas, menuX, menuY, menuX + menuW, menuY + menuH, 0xFFFFFFFF);
            for (int i = 0; i < modeLabels.length; i++) {
                float y = menuY + 20f + i * (menuH / modeLabels.length);
                drawText(canvas, modeLabels[i], menuX + menuW / 2f, y, textColor, 16f, Paint.Align.CENTER);
            }
        }

        private void drawRoundedRect(Canvas canvas, float left, float top, float right, float bottom, int color) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(color);
            canvas.drawRoundRect(new RectF(left, top, right, bottom), 12f, 12f, paint);
        }

        private void drawText(Canvas canvas, String text, float x, float y, int color, float size, Paint.Align align) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(color);
            paint.setTextAlign(align);
            paint.setTextSize(size);
            paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            canvas.drawText(text, x, y, paint);
        }

        private void refreshCandidates() {
            candidates.clear();
            if (composing == null || composing.isEmpty()) {
                invalidate();
                return;
            }
            String py = composing.toLowerCase(Locale.ROOT);
            String[] list = pinyinMap.get(py);
            if (list != null) {
                candidates.addAll(Arrays.asList(list));
            } else if (py.matches("[a-z]+")) {
                candidates.add(py);
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

        private void handleAlphabetInput(String input) {
            if ("En".equals(language)) {
                String letter = shift ? input.toUpperCase(Locale.ROOT) : input.toLowerCase(Locale.ROOT);
                commitText(letter);
                shift = false;
                invalidate();
                return;
            }
            if ("26键".equals(currentMode) || "T9".equals(currentMode)) {
                String key = input.toLowerCase(Locale.ROOT);
                if ("T9".equals(currentMode)) {
                    appendT9Key(key);
                    return;
                }
                composing += key;
                setComposingText(composing);
                refreshCandidates();
            } else {
                commitText(input);
            }
        }

        private void appendT9Key(String key) {
            if (!key.matches("[2-9]")) {
                return;
            }
            final String[] groups = {"", "", "abc", "def", "ghi", "jkl", "mno", "pqrs", "tuv", "wxyz"};
            String group = groups[Integer.parseInt(key)];
            long now = System.currentTimeMillis();
            if (lastT9Key.equals(key) && now - lastT9TapAt < 800 && !group.isEmpty()) {
                int currentIndex = group.indexOf(t9Buffer.charAt(t9Buffer.length() - 1));
                int nextIndex = (currentIndex + 1) % group.length();
                t9Buffer = String.valueOf(group.charAt(nextIndex));
            } else {
                t9Buffer = String.valueOf(group.charAt(0));
            }
            lastT9Key = key;
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

        private void handleModeSelection(int index) {
            if (index >= 0 && index < modeLabels.length) {
                currentMode = modeLabels[index];
                menuOpen = false;
                composing = "";
                t9Buffer = "";
                lastT9Key = "";
                candidates.clear();
                finishComposingText();
                invalidate();
            }
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
                    deleteSurroundingText();
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
            deleteAllCurrentText();
            invalidate();
        }

        private int detectBottomIndex(float x) {
            float[] cuts = {0.12f, 0.24f, 0.39f, 0.66f, 0.82f, 1.0f};
            for (int i = 0; i < cuts.length; i++) {
                if (x <= width * cuts[i]) return i;
            }
            return 5;
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            float x = event.getX();
            float y = event.getY();

            if (event.getAction() == MotionEvent.ACTION_DOWN && y >= height - bottomHeight && y <= height && x >= width * 0.82f && x <= width * 0.96f) {
                uiHandler.removeCallbacks(deleteLongPressRunnable);
                uiHandler.postDelayed(deleteLongPressRunnable, 500L);
            }
            if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                uiHandler.removeCallbacks(deleteLongPressRunnable);
            }

            if (event.getAction() != MotionEvent.ACTION_UP) {
                if ("手写".equals(currentMode) && y > topHeight + candHeight && y < height - bottomHeight) {
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

            if (y < topHeight) {
                int index = (int) (x / (width / 4f));
                if (index >= 0 && index <= 3) {
                    handleTopBarTap(index);
                }
                return true;
            }

            if (menuOpen && y >= topHeight && y <= topHeight + keyHeight * 0.62f) {
                float menuBoxTop = topHeight + 8f;
                float menuBoxHeight = keyHeight * 0.62f;
                int index = (int) ((y - menuBoxTop) / (menuBoxHeight / modeLabels.length));
                handleModeSelection(index);
                return true;
            }

            if (!candidates.isEmpty() && y >= topHeight && y < topHeight + candHeight) {
                float start = Math.max(90f, width * 0.32f);
                int index = (int) ((x - start) / ((width - start) / Math.max(1, Math.min(6, candidates.size()))));
                submitCandidate(Math.max(0, Math.min(candidates.size() - 1, index)));
                return true;
            }

            if (y >= height - bottomHeight) {
                int index = detectBottomIndex(x);
                handleBottomAction(index);
                return true;
            }

            if ("手写".equals(currentMode) && y > topHeight + candHeight && y < height - bottomHeight) {
                if (x > width - 110f && y > height - bottomHeight - 54f) {
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

            if (y > topHeight + candHeight && y < height - bottomHeight) {
                float rowHeight = keyHeight / 3f;
                int row = (int) ((y - (topHeight + candHeight)) / rowHeight);
                row = Math.max(0, Math.min(2, row));
                String[][] rows = { {"q", "w", "e", "r", "t", "y", "u", "i", "o", "p"},
                        {"a", "s", "d", "f", "g", "h", "j", "k", "l"},
                        {"z", "x", "c", "v", "b", "n", "m"} };
                if ("26键".equals(currentMode)) {
                    float cellWidth = width / rows[row].length;
                    int col = (int) (x / cellWidth);
                    col = Math.max(0, Math.min(rows[row].length - 1, col));
                    handleAlphabetInput(rows[row][col]);
                    return true;
                }
                if ("T9".equals(currentMode)) {
                    String[][] t9Rows = {{"1", "2", "3"}, {"4", "5", "6"}, {"7", "8", "9"}, {"*", "0", "#"}};
                    int r = (int) ((y - (topHeight + candHeight)) / (keyHeight / t9Rows.length));
                    r = Math.max(0, Math.min(t9Rows.length - 1, r));
                    float cellW = width / t9Rows[r].length;
                    int c = (int) (x / cellW);
                    c = Math.max(0, Math.min(t9Rows[r].length - 1, c));
                    handleAlphabetInput(t9Rows[r][c]);
                    return true;
                }
                if ("数字".equals(currentMode)) {
                    String[][] numRows = {{"1", "2", "3"}, {"4", "5", "6"}, {"7", "8", "9"}, {"-", "0", "."}};
                    int r = (int) ((y - (topHeight + candHeight)) / (keyHeight / numRows.length));
                    int c = (int) (x / (width / numRows[r].length));
                    commitText(numRows[r][c]);
                    return true;
                }
                if ("符号".equals(currentMode)) {
                    String[][] symRows = {{"。", "，", "？", "！", "；", "："},
                            {"、", "…", "（", "）", "“", "”"},
                            {"《", "》", "—", "￥", "@", "/"},
                            {"！", "？", "%", "&", "*", "…"}};
                    int r = (int) ((y - (topHeight + candHeight)) / (keyHeight / symRows.length));
                    r = Math.max(0, Math.min(symRows.length - 1, r));
                    int c = (int) (x / (width / symRows[r].length));
                    c = Math.max(0, Math.min(symRows[r].length - 1, c));
                    commitText(symRows[r][c]);
                    return true;
                }
            }
            return true;
        }
    }
}
