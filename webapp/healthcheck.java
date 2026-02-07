import java.net.*;

class healthcheck {
    public static void main(String[] args) throws Exception {
        var url = args.length > 0 ? args[0] : "http://localhost:8091/api/health";
        var c = (HttpURLConnection) URI.create(url).toURL().openConnection();
        c.setConnectTimeout(3000);
        c.setReadTimeout(3000);
        c.connect();
        System.exit(c.getResponseCode() == 200 ? 0 : 1);
    }
}
