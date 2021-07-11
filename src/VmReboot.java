
import java.io.*;
import java.util.*;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.*;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.*;
import org.apache.http.impl.client.*;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

/**
 * reboot virgin media super hub
 */
public class VmReboot {
	
	public static final String LOG = "log", COMMIT = "commit", PASS = "pass", HOST = "host";
	
	private static PrintWriter logpw;
	
	public static void main (String[] args) throws Exception {
		if (args.length > 1) {
			System.out.println("usage: java -jar httputil.jar VmReboot [vmreboot.properties]");
			return;
		}
		
		Properties p = Main.loadProps(new File(args.length > 0 ? args[0] : "vmreboot.properties"));
		String host = p.getProperty(HOST);
		String pass = p.getProperty(PASS);
		boolean commit = Boolean.parseBoolean(p.getProperty(COMMIT));
		File log = new File(p.getProperty(LOG), "vmreboot.log");
		
		System.out.println(HOST + " = " + host);
		System.out.println(PASS + " = " + pass);
		System.out.println(COMMIT + " = " + commit);
		System.out.println(LOG + " = " + log.getAbsolutePath() + " exists = " + log.exists());
		
		try (PrintWriter pw = new PrintWriter(new FileWriter(log, true))) {
			logpw = pw;
			
			for (int n = 0; n < 5; n++) {
				try (CloseableHttpClient client = HttpClients.createDefault()) {
					println("sleep 15s");
					Thread.sleep(15000);
					println("start " + n);
					String loginpath = getform(client, host, "/VmLogin.asp");
					postlogin(client, host, loginpath, pass);
					String rebootpath = getform(client, host, "/VmRgRebootRestoreDevice.asp");
					postreboot(client, host, rebootpath, commit);
					println("sleep 15s");
					Thread.sleep(15000);
					println("end " + n);
					return;
				} catch (Exception e) {
					printstacktrace(e);
				}
			}
		} 
	}
	
	private static void println (String msg) {
		System.out.println(msg);
		logpw.println(new Date() + ": " + msg);
	}
	
	private static void printstacktrace (Exception e) {
		e.printStackTrace(System.out);
		e.printStackTrace(logpw);
	}
	
	private static void postreboot (CloseableHttpClient client, String host, String rebootpath, boolean commit) throws Exception {
		// <input type="hidden" name="VmDeviceRestore" value=0 />
		// <input type="hidden" name="VmDeviceReboot" value=0 />
		List<NameValuePair> list = new ArrayList<>();
		list.add(new BasicNameValuePair("VmDeviceReboot", commit ? "1" : "0"));
		list.add(new BasicNameValuePair("VmDeviceRestore", "0"));
		HttpPost post = new HttpPost("http://" + host + rebootpath);
		println("post reboot " + post);
		post.setEntity(new UrlEncodedFormEntity(list));
		try (CloseableHttpResponse r = client.execute(post)) {
			println("response " + r.getStatusLine());
			int code = r.getStatusLine().getStatusCode();
			String body = EntityUtils.toString(r.getEntity());
			
			if (code == HttpStatus.SC_OK) {
				return;
			} else {
				println("body " + StringUtils.normalizeSpace(body));
				throw new Exception("unexpected status code " + code + " for reboot " + post);
			}
		}
	}
	
	private static void postlogin (CloseableHttpClient client, String host, String loginpath, String pass) throws Exception {
		// VmLoginUsername:
		// VmLoginPassword: ...
		// VmLoginErrorCode: 0
		// VmChangePasswordHint: 0
		List<NameValuePair> list = new ArrayList<>();
		list.add(new BasicNameValuePair("VmLoginUsername", ""));
		list.add(new BasicNameValuePair("VmLoginPassword", pass));
		list.add(new BasicNameValuePair("VmLoginErrorCode", "0"));
		list.add(new BasicNameValuePair("VmChangePasswordHint", "0"));
		HttpPost post = new HttpPost("http://" + host + loginpath);
		println("post login " + post);
		post.setEntity(new UrlEncodedFormEntity(list));
		
		try (CloseableHttpResponse r = client.execute(post)) {
			println("response " + r.getStatusLine());
			int code = r.getStatusLine().getStatusCode();
			String body = EntityUtils.toString(r.getEntity());
			
			if (code == HttpStatus.SC_MOVED_TEMPORARILY) {
				return;
			} else {
				println("body " + StringUtils.normalizeSpace(body));
				throw new Exception("unexpected status code " + code + " for login " + post);
			}
		}
	}
	
	private static String getform (CloseableHttpClient client, String host, String path) throws Exception {
		HttpGet get = new HttpGet("http://" + host + path);
		println("get form " + get);
		
		try (CloseableHttpResponse r = client.execute(get)) {
			println("status " + r.getStatusLine());
			String body = EntityUtils.toString(r.getEntity());
			int code = r.getStatusLine().getStatusCode();
			
			if (code == HttpStatus.SC_OK) {
				// it's not actually valid xml...
				int i = body.indexOf("/goform/");
				int j = i > 0 ? body.indexOf(" ", i) : 0;
				if (i > 0 && j > i) {
					String act = body.substring(i, j);
					if (act.endsWith("\"")) {
						act = act.substring(0, act.length() - 1);
					}
					println("action " + act);
					return act;
				} else {
					println("body " + StringUtils.normalizeSpace(body));
					throw new Exception("could not get action for " + get);
				}
			} else {
				println(StringUtils.normalizeSpace(body));
				throw new Exception("unexpected status code " + code + " for form " + get);
			}
		}
		
	}
}
