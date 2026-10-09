package nhs.ot.equiptrack.discharge;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// HMAC-SHA256 of the MRN's digits. MRNs are short numbers, so a plain hash
// could be reversed by trying them all; the secret key (MRN_HASH_KEY) prevents that.
@Component
public class MrnHasher {

	private final SecretKeySpec key;

	MrnHasher(@Value("${app.mrn.hash-key}") String hashKey) {
		if (hashKey == null || hashKey.isBlank()) {
			throw new IllegalStateException("MRN_HASH_KEY must be set");
		}
		this.key = new SecretKeySpec(hashKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
	}

	public static String digits(String mrn) {
		return mrn == null ? "" : mrn.replaceAll("\\D", "");
	}

	public String hash(String mrn) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(key);
			return HexFormat.of().formatHex(mac.doFinal(digits(mrn).getBytes(StandardCharsets.UTF_8)));
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException(e);
		}
	}

}
