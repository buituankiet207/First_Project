package vn.uth.lms.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A generated report: table + optional chart + summary (shown on screen and exported as CSV). */
public class Report {
    private final String title;
    private final List<String> columns;
    private final List<List<Object>> rows = new ArrayList<>();
    private final Map<String, Object> summary = new LinkedHashMap<>();
    private final List<String> chartLabels = new ArrayList<>();
    private final List<Number> chartValues = new ArrayList<>();

    public Report(String title, List<String> columns) {
        this.title = title;
        this.columns = columns;
    }

    public Report row(Object... cells) { rows.add(java.util.Arrays.asList(cells)); return this; }
    public Report summary(String key, Object value) { summary.put(key, value); return this; }
    public Report chart(String label, Number value) { chartLabels.add(label); chartValues.add(value); return this; }

    public String getTitle() { return title; }
    public List<String> getColumns() { return columns; }
    public List<List<Object>> getRows() { return rows; }
    public boolean isEmpty() { return rows.isEmpty(); }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("title", title);
        m.put("columns", columns);
        m.put("rows", rows);
        m.put("summary", summary);
        m.put("chartLabels", chartLabels);
        m.put("chartValues", chartValues);
        m.put("empty", rows.isEmpty());
        return m;
    }

    public String toCsv() {
        StringBuilder sb = new StringBuilder("\uFEFF");
        sb.append(String.join(",", columns.stream().map(Report::csv).toList())).append("\r\n");
        for (List<Object> r : rows) sb.append(String.join(",", r.stream().map(c -> csv(c == null ? "" : c.toString())).toList())).append("\r\n");
        return sb.toString();
    }

    private static String csv(String s) {
        return (s.contains(",") || s.contains("\"") || s.contains("\n")) ? "\"" + s.replace("\"", "\"\"") + "\"" : s;
    }
}
