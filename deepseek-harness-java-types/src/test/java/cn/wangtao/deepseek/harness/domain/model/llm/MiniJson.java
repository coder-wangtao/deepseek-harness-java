package cn.wangtao.deepseek.harness.domain.model.llm;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 教学用迷你 JSON 解析/序列化工具（仅测试代码使用）。
 *
 * <p>types 模块坚持零第三方依赖，因此这里手写一个 ~150 行的递归下降 JSON 实现，
 * 用于：1) 构造发往 LLM 的请求报文；2) 解析 LLM 返回的响应报文；
 * 3) 把模型给出的工具调用 arguments（JSON 字符串）解析成 Map 交给工具执行。</p>
 *
 * <p>支持类型：object → {@code Map<String,Object>}，array → {@code List<Object>}，
 * string → {@code String}，整数 → {@code Long}，小数 → {@code Double}，
 * true/false → {@code Boolean}，null → {@code null}。</p>
 *
 * @author 小傅哥
 * @website bugstack.cn
 */
final class MiniJson {

    /** 待解析源串与游标。 */
    private final String src;
    private int pos;

    private MiniJson(String src) {
        this.src = src;
    }

    /** 解析一段 JSON 文本为 Java 对象树。 */
    static Object parse(String json) {
        MiniJson p = new MiniJson(json);
        p.skipWs();
        return p.parseValue();
    }

    /** 把 Java 对象树（Map/List/String/Number/Boolean/null）序列化为 JSON 文本。 */
    static String write(Object value) {
        StringBuilder sb = new StringBuilder();
        writeTo(value, sb);
        return sb.toString();
    }

    // ── 解析 ───────────────────────────────────────────────────────────

    private Object parseValue() {
        skipWs();
        char c = peek();
        switch (c) {
            case '{': return parseObject();
            case '[': return parseArray();
            case '"': return parseString();
            case 't': expect("true"); return Boolean.TRUE;
            case 'f': expect("false"); return Boolean.FALSE;
            case 'n': expect("null"); return null;
            default: return parseNumber();
        }
    }

    private Map<String, Object> parseObject() {
        Map<String, Object> map = new LinkedHashMap<>();
        pos++; // 吃掉 '{'
        skipWs();
        if (peek() == '}') { pos++; return map; }
        while (true) {
            skipWs();
            String key = parseString();
            skipWs();
            expectChar(':');
            map.put(key, parseValue());
            skipWs();
            char c = src.charAt(pos++);
            if (c == '}') return map;
            if (c != ',') throw err("expected ',' or '}'");
        }
    }

    private List<Object> parseArray() {
        List<Object> list = new ArrayList<>();
        pos++; // 吃掉 '['
        skipWs();
        if (peek() == ']') { pos++; return list; }
        while (true) {
            list.add(parseValue());
            skipWs();
            char c = src.charAt(pos++);
            if (c == ']') return list;
            if (c != ',') throw err("expected ',' or ']'");
        }
    }

    private String parseString() {
        expectChar('"');
        StringBuilder sb = new StringBuilder();
        while (true) {
            char c = src.charAt(pos++);
            if (c == '"') return sb.toString();
            if (c != '\\') { sb.append(c); continue; }
            char e = src.charAt(pos++);
            switch (e) {
                case '"' -> sb.append('"');
                case '\\' -> sb.append('\\');
                case '/' -> sb.append('/');
                case 'b' -> sb.append('\b');
                case 'f' -> sb.append('\f');
                case 'n' -> sb.append('\n');
                case 'r' -> sb.append('\r');
                case 't' -> sb.append('\t');
                case 'u' -> {
                    sb.append((char) Integer.parseInt(src.substring(pos, pos + 4), 16));
                    pos += 4;
                }
                default -> throw err("bad escape \\" + e);
            }
        }
    }

    private Number parseNumber() {
        int start = pos;
        while (pos < src.length() && "+-0123456789.eE".indexOf(src.charAt(pos)) >= 0) pos++;
        String num = src.substring(start, pos);
        if (num.indexOf('.') >= 0 || num.indexOf('e') >= 0 || num.indexOf('E') >= 0) {
            return Double.parseDouble(num);
        }
        return Long.parseLong(num);
    }

    private void skipWs() {
        while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) pos++;
    }

    private char peek() {
        if (pos >= src.length()) throw err("unexpected end");
        return src.charAt(pos);
    }

    private void expect(String literal) {
        if (!src.startsWith(literal, pos)) throw err("expected " + literal);
        pos += literal.length();
    }

    private void expectChar(char c) {
        if (peek() != c) throw err("expected '" + c + "'");
        pos++;
    }

    private IllegalStateException err(String message) {
        return new IllegalStateException("MiniJson: " + message + " at pos " + pos);
    }

    // ── 序列化 ─────────────────────────────────────────────────────────

    private static void writeTo(Object v, StringBuilder sb) {
        if (v == null) {
            sb.append("null");
        } else if (v instanceof String s) {
            writeString(s, sb);
        } else if (v instanceof Boolean b) {
            sb.append(b.booleanValue());
        } else if (v instanceof Map<?, ?> m) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                writeString(String.valueOf(e.getKey()), sb);
                sb.append(':');
                writeTo(e.getValue(), sb);
            }
            sb.append('}');
        } else if (v instanceof Iterable<?> it) {
            sb.append('[');
            boolean first = true;
            for (Object item : it) {
                if (!first) sb.append(',');
                first = false;
                writeTo(item, sb);
            }
            sb.append(']');
        } else if (v instanceof Number n) {
            sb.append(n);
        } else {
            // 兜底：其余对象按字符串处理，避免教学费工夫在类型系统上
            writeString(String.valueOf(v), sb);
        }
    }

    private static void writeString(String s, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        sb.append('"');
    }
}
