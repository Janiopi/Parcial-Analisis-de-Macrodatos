package pe.edu.cc531;

import java.nio.charset.Charset;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.apache.hadoop.io.Text;

/** Schema and decoding for the PNDA MTC snapshot (22 semicolon-delimited fields). */
public final class Data {
    private Data() {}
    public static final Charset SOURCE_CHARSET = Charset.forName("windows-1252");
    public static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/uuuu", Locale.ROOT);
    public static final LocalDate CUT = LocalDate.of(2026, 8, 20);

    public static final class Row {
        public final String[] f;
        Row(String[] fields) { f = fields; }
        public int id() { return Integer.parseInt(f[0]); }
        public LocalDate occurrence() { return LocalDate.parse(f[2], DATE); }
        public String event() { return f[5].trim(); }
        public String department() { return f[6].trim(); }
        public String road() { return f[10].trim(); }
        public String initialState() { return f[16].trim(); }
        public LocalDate closure() { return f[17].isEmpty() ? null : LocalDate.parse(f[17], DATE); }
        public double lat() { return Double.parseDouble(f[19].trim().replace(',', '.')); }
        public double lon() { return Double.parseDouble(f[20].trim().replace(',', '.')); }
    }

    public static Row parse(Text value) {
        String line = new String(value.getBytes(), 0, value.getLength(), SOURCE_CHARSET);
        if (line.startsWith("ID_EMERGENCIA;")) return null;
        String[] f = line.split(";", -1);
        if (f.length != 22) throw new IllegalArgumentException("Expected 22 columns, got " + f.length);
        Row row = new Row(f);
        row.id(); row.occurrence(); row.lat(); row.lon();
        if (row.lat() < -90 || row.lat() > 90 || row.lon() < -180 || row.lon() > 180)
            throw new IllegalArgumentException("Invalid coordinates");
        return row;
    }

    public static Row safeParse(Text value, org.apache.hadoop.mapreduce.TaskInputOutputContext<?, ?, ?, ?> ctx) {
        try { return parse(value); }
        catch (RuntimeException ex) { ctx.getCounter("input", "invalid_rows").increment(1); return null; }
    }

    public static double haversine(double lat1, double lon1, double lat2, double lon2) {
        double a = Math.toRadians(lat1), b = Math.toRadians(lat2);
        double dlat = b - a, dlon = Math.toRadians(lon2-lon1);
        double h = Math.sin(dlat/2)*Math.sin(dlat/2)
            + Math.cos(a)*Math.cos(b)*Math.sin(dlon/2)*Math.sin(dlon/2);
        return 6371.0088 * 2 * Math.atan2(Math.sqrt(h), Math.sqrt(Math.max(0, 1-h)));
    }
}
