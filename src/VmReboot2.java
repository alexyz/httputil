import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.*;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.cookie.ClientCookie;
import org.apache.http.impl.client.*;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.http.util.EntityUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class VmReboot2 implements AutoCloseable {

    public static void main(String[] args) throws Exception {
        if (args.length > 1) {
            System.out.println("usage: java -jar httputil.jar VmReboot2");
            return;
        }

        Properties p = Main.loadProps(new File("vmreboot2.properties"));

        VmReboot2 vm = new VmReboot2();
        vm.host = p.getProperty(VmReboot.HOST);
        vm.pass = p.getProperty(VmReboot.PASS);
        vm.commit = Boolean.parseBoolean(p.getProperty(VmReboot.COMMIT));
        vm.log = new File(p.getProperty(VmReboot.LOG), "vmreboot2.log");

        System.out.println("vm: " + vm);

        vm.login();
        vm.reboot();
        vm.logout();
    }

    private final BasicCookieStore cookieStore = new BasicCookieStore();
    private final CloseableHttpClient client;
    private final HttpClientContext context;
    private final String nonce;

    private String host, pass;
    private boolean commit;
    private File log;

    public VmReboot2() {
        this.client = HttpClients.createDefault();
        this.nonce = String.format("%05d", Main.RANDOM.nextInt(100000));
        this.context = HttpClientContext.create();
        context.setAttribute(HttpClientContext.COOKIE_STORE, cookieStore);
    }

    public void login() throws Exception {
        println("login");

        // Request URL: http://192.168.100.1/login?arg=YWRtaW46NzU2MDU1NTE=&_n=02414&_=1624187581555
        //arg is base64(admin:75605551)
        //_n is random number
        //_ is current time millis
        // url parameters must be literal, not url encoded...
        String arg = Base64.getEncoder().encodeToString(("admin:" + pass).getBytes(StandardCharsets.UTF_8));
        HttpGet get = new HttpGet(String.format("http://%s/login?arg=%s&_n=%s&_=%s", host, arg, nonce, System.currentTimeMillis()));
        println(get);

        try (CloseableHttpResponse r = client.execute(get)) {
            String body = EntityUtils.toString(r.getEntity());
            println("response " + r.getStatusLine());
            println(body);
            if (r.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
                println(new String(Base64.getDecoder().decode(body), StandardCharsets.UTF_8));
                setCredential(body);
            } else {
                throw new Exception("unexpected login response: " + r.getStatusLine().getStatusCode());
            }
        }
    }

    private void setCredential(String body) {
        cookieStore.clear();
        BasicClientCookie cookie = new BasicClientCookie("credential", body);
        cookie.setDomain(host);
        cookie.setAttribute(ClientCookie.DOMAIN_ATTR, "true");
        cookie.setPath("/");
        cookieStore.addCookie(cookie);
    }

    private void reboot() throws Exception {
        if (commit) {
            println("reboot");
            HttpGet get = new HttpGet(String.format("http://%s/snmpSet?oid=1.3.6.1.2.1.69.1.1.3.0=2;2;&_n=%s&_=%s", host, nonce, System.currentTimeMillis()));
            println(get);
            try (CloseableHttpResponse r = client.execute(get, context)) {
                String body = EntityUtils.toString(r.getEntity());
                println(r.getStatusLine());
                println(StringUtils.normalizeSpace(body));
                // any response ok?
            }
        } else {
            println("not rebooting");
        }
    }

    private void logout() throws IOException {
        HttpGet get = new HttpGet(String.format("http://%s/logout?_n=%s&_=%s", host, nonce, System.currentTimeMillis()));
        println("logout");
        println(get);
        try (CloseableHttpResponse r = client.execute(get, context)) {
            String body = EntityUtils.toString(r.getEntity());
            println(r.getStatusLine());
            println(StringUtils.normalizeSpace(body));
            // any response is ok
        }
    }

    private void println(Object msg) {
        System.out.println(msg);
        try (PrintWriter pw = new PrintWriter(new FileWriter(log, true))) {
            pw.println(new Date() + ": " + msg);
        } catch (IOException e) {
            System.out.println(e);
        }
    }

    @Override
    public String toString() {
        return "VmReboot2[host=" + host + " pass=" + pass + " commit=" + commit + " log=" + log.getName() + "]";
    }

    @Override
    public void close() throws Exception {
        client.close();
    }
}
