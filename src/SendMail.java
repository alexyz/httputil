
import static org.apache.commons.lang3.StringUtils.contains;
import static org.apache.commons.lang3.StringUtils.isBlank;

import java.io.File;
import java.io.IOException;
import java.util.Base64;
import java.util.Date;
import java.util.Properties;

import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.apache.commons.lang3.StringUtils;

public class SendMail {
	
	public static void main (String[] args) throws Exception {
		String l = Main.readLine();
		if (l.length() > 0) {
			Main.println(Base64.getEncoder().encodeToString(l.getBytes()));
		}
	}
	
	public static SendMail create () throws IOException {
		File f = new File("sendmail.properties");
		if (f.exists()) {
			Properties p = Main.loadProps(f);
			SendMail s = new SendMail();
			s.host = p.getProperty("host");
			s.port = Integer.parseInt(p.getProperty("port", "0"));
			s.from = p.getProperty("from");
			s.fromname = p.getProperty("fromname");
			s.auth = Boolean.parseBoolean(p.getProperty("auth"));
			s.ssl = Boolean.parseBoolean(p.getProperty("ssl"));
			s.user = p.getProperty("user");
			String pass = p.getProperty("pass");
			s.pass = pass != null ? new String(Base64.getDecoder().decode(pass)) : null;
			s.starttls = Boolean.parseBoolean(p.getProperty("starttls"));
			s.debug = Boolean.parseBoolean(p.getProperty("debug"));
			return s;
		} else {
			return null;
		}
	}
	
	private String host, user, pass, from, fromname;
	private boolean auth, ssl, starttls, debug;
	private int port;
	
	public SendMail() {
		//
	}
	
	public void send (String to, String sub, String text) {
		Main.println("send mail " + to + "\n" + sub + "\n" + text);
		
		if (isBlank(host) || !contains(from, "@") || (auth && (isBlank(user) || isBlank(pass)))) {
			Main.println("send mail invalid config: h=" + host + ", f=" + from + ", a=" + auth + ", u=" + user + ", p=" + (pass != null));
			return;
		}
		
		if (!contains(to, "@") || isBlank(sub) || isBlank(text)) {
			Main.println("send mail invalid params: t=" + to + ", s=" + sub + ", t=" + StringUtils.normalizeSpace(text));
			return;
		}
		
		try {
			Properties p = new Properties();
			p.setProperty("mail.smtp.host", host);
			p.setProperty("mail.smtp.from", from);
			if (port != 0)
				p.setProperty("mail.smtp.port", String.valueOf(port));
			if (auth)
				p.setProperty("mail.smtp.auth", String.valueOf(auth));
			if (ssl)
				p.setProperty("mail.smtp.ssl.enable", String.valueOf(ssl));
			if (user != null)
				p.setProperty("mail.smtp.user", user);
			if (pass != null)
				p.setProperty("mail.smtp.password", pass);
			if (starttls)
				p.setProperty("mail.smtp.starttls.required", String.valueOf(starttls));
			if (debug)
				p.setProperty("mail.debug", String.valueOf(debug));
			p.setProperty("mail.smtp.connectiontimeout", "60000");
			p.setProperty("mail.smtp.timeout", "60000");
			p.setProperty("mail.smtp.writetimeout", "60000");
			
			Session s = Session.getInstance(p, new Authenticator() {
				@Override
				protected PasswordAuthentication getPasswordAuthentication () {
					//Main.println("authenticate");
					return new PasswordAuthentication(user, pass);
				}
			});
			
			InternetAddress fromadd = new InternetAddress(from, fromname);
			Message msg = new MimeMessage(s);
			msg.setReplyTo(new Address[] { fromadd });
			msg.setFrom(fromadd);
			msg.setRecipients(Message.RecipientType.TO, new Address[] { new InternetAddress(to) });
			msg.setSubject(sub);
			msg.setSentDate(new Date());
			msg.setText(text);
			
			Transport.send(msg);
			
		} catch (RuntimeException e) {
			throw e;
			
		} catch (Exception e) {
			Main.println("could not send: " + e);
		}
	}
}
