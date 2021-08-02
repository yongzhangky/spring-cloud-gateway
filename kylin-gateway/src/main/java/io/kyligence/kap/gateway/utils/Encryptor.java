/*
 * Copyright (C) 2020 Kyligence Inc. All rights reserved.
 *
 * http://kyligence.io
 *
 * This software is the confidential and proprietary information of
 * Kyligence Inc. ("Confidential Information"). You shall not disclose
 * such Confidential Information and shall use it only in accordance
 * with the terms of the license agreement you entered into with
 * Kyligence Inc.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */


package io.kyligence.kap.gateway.utils;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.NoSuchAlgorithmException;

@Slf4j
public class Encryptor {

	private static final String SECRET_KEY = "1fb511361580867f62c71b08f9db72f3";

	private static final String KEY_ALGORITHM = "AES";

	private static final String DEFAULT_CIPHER_ALGORITHM = "AES/ECB/PKCS5Padding";

	private static final Key KEY;

	static {
		try {
			KEY = toKey(Hex.decodeHex(SECRET_KEY));
		} catch (DecoderException e) {
			throw new Error("Load AES key error", e);
		}
	}

	public static byte[] initSecretKey() {
		KeyGenerator kg;
		try {
			kg = KeyGenerator.getInstance(KEY_ALGORITHM);
			kg.init(128);
			SecretKey secretKey = kg.generateKey();
			return secretKey.getEncoded();
		} catch (NoSuchAlgorithmException e) {
			log.error("no such encryption algorithm", e);
			return new byte[0];
		}
	}

	private static Key toKey(byte[] key) {
		return new SecretKeySpec(key, KEY_ALGORITHM);
	}

	public static String encrypt(String plainText) {
		byte[] bytes = plainText.getBytes(StandardCharsets.UTF_8);
		byte[] encryptBytes = encrypt(bytes);
		return Hex.encodeHexString(encryptBytes);
	}

	private static byte[] encrypt(byte[] data) {
		try {
			Cipher cipher = Cipher.getInstance(DEFAULT_CIPHER_ALGORITHM);
			cipher.init(Cipher.ENCRYPT_MODE, KEY);
			return cipher.doFinal(data);
		} catch (Exception ex) {
			log.error("Password Encryption error", ex);
			return data;
		}
	}

	public static String decrypt(String cipherHexText) throws Exception {
		byte[] cipherBytes;
		try {
			cipherBytes = Hex.decodeHex(cipherHexText);
		} catch (Exception e) {
			throw new Exception("Password decodeHex error", e);
		}

		byte[] clearBytes = decrypt(cipherBytes);
		return new String(clearBytes, StandardCharsets.UTF_8);
	}

	private static byte[] decrypt(byte[] data) throws Exception {
		try {
			Cipher cipher = Cipher.getInstance(DEFAULT_CIPHER_ALGORITHM);
			cipher.init(Cipher.DECRYPT_MODE, KEY);
			return cipher.doFinal(data);
		} catch (Exception ex) {
			throw new Exception("Password Decryption error", ex);
		}
	}
}



