package com.avob.openadr.server.oadr20b.ven;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import javax.xml.bind.JAXBException;

import org.apache.logging.log4j.core.util.FileUtils;
import org.jivesoftware.smack.SmackException.NotConnectedException;
import org.jxmpp.stringprep.XmppStringprepException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.PropertiesFactoryBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;

import com.avob.openadr.client.http.OadrHttpClientBuilder;
import com.avob.openadr.client.http.oadr20b.OadrHttpClient20b;
import com.avob.openadr.client.http.oadr20b.ven.OadrHttpVenClient20b;
import com.avob.openadr.client.xmpp.oadr20b.OadrXmppClient20b;
import com.avob.openadr.client.xmpp.oadr20b.OadrXmppException;
import com.avob.openadr.client.xmpp.oadr20b.ven.OadrXmppVenClient20b;
import com.avob.openadr.model.oadr20b.Oadr20bSecurity;
import com.avob.openadr.model.oadr20b.exception.Oadr20bException;
import com.avob.openadr.model.oadr20b.exception.Oadr20bHttpLayerException;
import com.avob.openadr.model.oadr20b.exception.Oadr20bMarshalException;
import com.avob.openadr.model.oadr20b.exception.Oadr20bXMLSignatureException;
import com.avob.openadr.model.oadr20b.exception.Oadr20bXMLSignatureValidationException;
import com.avob.openadr.model.oadr20b.oadr.OadrCancelOptType;
import com.avob.openadr.model.oadr20b.oadr.OadrCancelPartyRegistrationType;
import com.avob.openadr.model.oadr20b.oadr.OadrCancelReportType;
import com.avob.openadr.model.oadr20b.oadr.OadrCanceledPartyRegistrationType;
import com.avob.openadr.model.oadr20b.oadr.OadrCanceledReportType;
import com.avob.openadr.model.oadr20b.oadr.OadrCreateOptType;
import com.avob.openadr.model.oadr20b.oadr.OadrCreatePartyRegistrationType;
import com.avob.openadr.model.oadr20b.oadr.OadrCreateReportType;
import com.avob.openadr.model.oadr20b.oadr.OadrCreatedEventType;
import com.avob.openadr.model.oadr20b.oadr.OadrCreatedReportType;
import com.avob.openadr.model.oadr20b.oadr.OadrPollType;
import com.avob.openadr.model.oadr20b.oadr.OadrQueryRegistrationType;
import com.avob.openadr.model.oadr20b.oadr.OadrRegisterReportType;
import com.avob.openadr.model.oadr20b.oadr.OadrRequestEventType;
import com.avob.openadr.model.oadr20b.oadr.OadrResponseType;
import com.avob.openadr.model.oadr20b.oadr.OadrUpdateReportType;
import com.avob.openadr.model.oadr20b.oadr.OadrUpdatedReportType;
import com.avob.openadr.security.OadrHttpSecurity;
import com.avob.openadr.security.exception.OadrSecurityException;
import com.avob.openadr.server.oadr20b.ven.exception.OadrVTNInitializationException;
import com.avob.openadr.server.oadr20b.ven.xmpp.XmppVenListener;

@Configuration
public class MultiVtnConfig {

	private static final Logger LOGGER = LoggerFactory.getLogger(MultiVtnConfig.class);

	private static final Pattern venConfigurationFilePatter = Pattern.compile("vtn\\..*\\.properties");

	@Resource
	private XmppVenListener xmppVenListeners;

	@Resource
	private VenConfig venConfig;

	@Value("${ven.home:#{null}}")
	private String venHome;

	private Map<String, VtnSessionConfiguration> multiConfig = new HashMap<String, VtnSessionConfiguration>();

	private Map<String, OadrHttpVenClient20b> multiHttpClientConfig = new HashMap<String, OadrHttpVenClient20b>();

	private Map<String, OadrXmppVenClient20b> multiXmppClientConfig = new HashMap<String, OadrXmppVenClient20b>();

	private void configureClient(VtnSessionConfiguration session)
			throws OadrSecurityException, JAXBException, OadrVTNInitializationException {

		if (session.getVtnUrl() != null) {
			OadrHttpClientBuilder builder = new OadrHttpClientBuilder().withDefaultHost(session.getVtnUrl())
					.withTrustedCertificate(
							new ArrayList<String>(session.getVenSessionConfig().getVtnTrustCertificate().values()))
					.withPooling(1, 1).withProtocol(Oadr20bSecurity.getProtocols(), Oadr20bSecurity.getCiphers());

			if (session.isBasicAuthenticationConfigured()) {
				LOGGER.info("Init HTTP VEN client with basic authentication");
				builder.withDefaultBasicAuthentication(session.getVtnUrl(),
						session.getVenSessionConfig().getBasicUsername(),
						session.getVenSessionConfig().getBasicPassword());

			} else if (session.isDigestAuthenticationConfigured()) {
				LOGGER.info("Init HTTP VEN client with digest authentication");
				builder.withDefaultDigestAuthentication(session.getVtnUrl(), "", "",
						session.getVenSessionConfig().getDigestUsername(),
						session.getVenSessionConfig().getDigestPassword());

			} else {
				builder.withX509Authentication(session.getVenSessionConfig().getVenPrivateKeyPath(),
						session.getVenSessionConfig().getVenCertificatePath());
			}

			OadrHttpVenClient20b client = null;
			if (venConfig.getXmlSignature()) {
				new OadrHttpVenClient20b(
						new OadrHttpClient20b(builder.build(), session.getVenSessionConfig().getVenPrivateKeyPath(),
								session.getVenSessionConfig().getVenCertificatePath(),
								session.getVenSessionConfig().getReplayProtectAcceptedDelaySecond()));
			} else {
				new OadrHttpVenClient20b(new OadrHttpClient20b(builder.build()));
			}

			getMultiHttpClientConfig().put(session.getVtnId(), client);
		} else if (session.getVtnXmppHost() != null && session.getVtnXmppPort() != null) {

			String keystorePassword = UUID.randomUUID().toString();

			KeyStore keystore;
			try {
//				String oadr20bFingerprint = OadrHttpSecurity
//						.getOadr20bFingerprint(session.getVenSessionConfig().getVenCertificatePath());

				keystore = OadrHttpSecurity.createKeyStore(session.getVenSessionConfig().getVenPrivateKeyPath(),
						session.getVenSessionConfig().getVenCertificatePath(), keystorePassword);
				KeyStore truststore = OadrHttpSecurity
						.createTrustStore(session.getVenSessionConfig().getVtnTrustCertificate());

				// init key manager factory
				KeyStore createKeyStore = keystore;
				KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
				kmf.init(createKeyStore, keystorePassword.toCharArray());

				// init trust manager factory
				TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
				tmf.init(truststore);

				// SSL Context Factory
				SSLContext sslContext = SSLContext.getInstance("TLS");

				// init ssl context
				String seed = UUID.randomUUID().toString();

				sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), new SecureRandom(seed.getBytes()));

				OadrXmppClient20b oadrXmppClient20b = new OadrXmppClient20b(session.getVtnId(),
						session.getVtnXmppHost(), session.getVtnXmppPort(), "client", sslContext, xmppVenListeners);

				OadrXmppVenClient20b venClient = new OadrXmppVenClient20b(oadrXmppClient20b);
				getMultiXmppClientConfig().put(session.getVtnId(), venClient);

			} catch (KeyStoreException e) {
				throw new OadrVTNInitializationException(e);
			} catch (NoSuchAlgorithmException e) {
				throw new OadrVTNInitializationException(e);
			} catch (CertificateException e) {
				throw new OadrVTNInitializationException(e);
			} catch (IOException e) {
				throw new OadrVTNInitializationException(e);
			} catch (OadrSecurityException e) {
				throw new OadrVTNInitializationException(e);
			} catch (UnrecoverableKeyException e) {
				throw new OadrVTNInitializationException(e);
			} catch (OadrXmppException e) {
				throw new OadrVTNInitializationException(e);
			} catch (KeyManagementException e) {
				throw new OadrVTNInitializationException(e);
			}

		}

	}

	@PostConstruct
	public void init() {
		if (venHome == null) {
			LOGGER.error("Ven home config must point to a local folder");
			throw new IllegalArgumentException("Ven home config must point to a local folder");
		}
		URI uri;
		try {
			uri = new URI(venHome);
			File fileFromUri = FileUtils.fileFromUri(uri);
			if (fileFromUri.isDirectory()) {

				for (File file : fileFromUri.listFiles()) {

					Matcher matcher = MultiVtnConfig.venConfigurationFilePatter.matcher(file.getName());
					if (matcher.find()) {

						PropertiesFactoryBean propertiesFactoryBean = new PropertiesFactoryBean();
						propertiesFactoryBean.setLocation(new FileSystemResource(file.getAbsolutePath()));
						try {
							propertiesFactoryBean.afterPropertiesSet();
							Properties properties = propertiesFactoryBean.getObject();
							VtnSessionConfiguration session = new VtnSessionConfiguration(properties, venConfig);
							LOGGER.debug("Valid vtn configuration file: " + file.getName());
							LOGGER.info(session.toString());
							getMultiConfig().put(session.getVtnId(), session);
							configureClient(session);

						} catch (IOException e) {
							LOGGER.error("File: " + file.getName() + " is not a valid vtn configuration file", e);
						} catch (OadrSecurityException e) {
							LOGGER.error("File: " + file.getName() + " is not a valid vtn configuration file", e);
						} catch (JAXBException e) {
							LOGGER.error("File: " + file.getName() + " is not a valid vtn configuration file", e);
						} catch (OadrVTNInitializationException e) {
							LOGGER.error(e.getMessage());
						}
					}
				}

			} else {
				LOGGER.error("Ven home config must point to a local folder");
				throw new IllegalArgumentException("Ven home config must point to a local folder");
			}
		} catch (URISyntaxException e) {
			LOGGER.error("Avob home config must point to a local folder", e);
		}

	}

	public Map<String, VtnSessionConfiguration> getMultiConfig() {
		return multiConfig;
	}

	public VtnSessionConfiguration getMultiConfig(String vtnId) {
		return multiConfig.get(vtnId);
	}

	public Map<String, OadrHttpVenClient20b> getMultiHttpClientConfig() {
		return multiHttpClientConfig;
	}

	public OadrHttpVenClient20b getMultiHttpClientConfig(VtnSessionConfiguration vtnConfiguration) {
		return multiHttpClientConfig.get(vtnConfiguration.getVtnId());
	}

	public void oadrCreatedReport(VtnSessionConfiguration vtnConfiguration, OadrCreatedReportType payload)
			throws Oadr20bException, Oadr20bHttpLayerException, Oadr20bXMLSignatureException,
			Oadr20bXMLSignatureValidationException, XmppStringprepException, NotConnectedException,
			Oadr20bMarshalException, InterruptedException {

		if (vtnConfiguration.getVtnUrl() != null) {
			multiHttpClientConfig.get(vtnConfiguration.getVtnId()).oadrCreatedReport(payload);
		} else if (vtnConfiguration.getVtnXmppHost() != null && vtnConfiguration.getVtnXmppPort() != null) {
			multiXmppClientConfig.get(vtnConfiguration.getVtnId()).oadrCreatedReport(payload);
		}

	}

	public void oadrCreateReport(VtnSessionConfiguration vtnConfiguration, OadrCreateReportType payload)
			throws Oadr20bException, Oadr20bHttpLayerException, Oadr20bXMLSignatureException,
			Oadr20bXMLSignatureValidationException, XmppStringprepException, NotConnectedException,
			Oadr20bMarshalException, InterruptedException {
		if (vtnConfiguration.getVtnUrl() != null) {
			multiHttpClientConfig.get(vtnConfiguration.getVtnId()).oadrCreateReport(payload);
		} else if (vtnConfiguration.getVtnXmppHost() != null && vtnConfiguration.getVtnXmppPort() != null) {
			multiXmppClientConfig.get(vtnConfiguration.getVtnId()).oadrCreateReport(payload);
		}
	}

	public void oadrUpdateReport(VtnSessionConfiguration vtnConfiguration, OadrUpdateReportType payload)
			throws Oadr20bException, Oadr20bHttpLayerException, Oadr20bXMLSignatureException,
			Oadr20bXMLSignatureValidationException, XmppStringprepException, NotConnectedException,
			Oadr20bMarshalException, InterruptedException {
		if (vtnConfiguration.getVtnUrl() != null) {
			multiHttpClientConfig.get(vtnConfiguration.getVtnId()).oadrUpdateReport(payload);
		} else if (vtnConfiguration.getVtnXmppHost() != null && vtnConfiguration.getVtnXmppPort() != null) {
			multiXmppClientConfig.get(vtnConfiguration.getVtnId()).oadrUpdateReport(payload);
		}
	}

	public void oadrUpdatedReport(VtnSessionConfiguration vtnConfiguration, OadrUpdatedReportType payload)
			throws Oadr20bException, Oadr20bHttpLayerException, Oadr20bXMLSignatureException,
			Oadr20bXMLSignatureValidationException, XmppStringprepException, NotConnectedException,
			Oadr20bMarshalException, InterruptedException {
		if (vtnConfiguration.getVtnUrl() != null) {
			multiHttpClientConfig.get(vtnConfiguration.getVtnId()).oadrUpdatedReport(payload);
		} else if (vtnConfiguration.getVtnXmppHost() != null && vtnConfiguration.getVtnXmppPort() != null) {
			multiXmppClientConfig.get(vtnConfiguration.getVtnId()).oadrUpdatedReport(payload);
		}
	}

	public void oadrCancelReport(VtnSessionConfiguration vtnConfiguration, OadrCancelReportType payload)
			throws Oadr20bException, Oadr20bHttpLayerException, Oadr20bXMLSignatureException,
			Oadr20bXMLSignatureValidationException, XmppStringprepException, NotConnectedException,
			Oadr20bMarshalException, InterruptedException {
		if (vtnConfiguration.getVtnUrl() != null) {
			multiHttpClientConfig.get(vtnConfiguration.getVtnId()).oadrCancelReport(payload);
		} else if (vtnConfiguration.getVtnXmppHost() != null && vtnConfiguration.getVtnXmppPort() != null) {
			multiXmppClientConfig.get(vtnConfiguration.getVtnId()).oadrCancelReport(payload);
		}
	}

	public void oadrCanceledReport(VtnSessionConfiguration vtnConfiguration, OadrCanceledReportType payload)
			throws Oadr20bException, Oadr20bHttpLayerException, Oadr20bXMLSignatureException,
			Oadr20bXMLSignatureValidationException, XmppStringprepException, NotConnectedException,
			Oadr20bMarshalException, InterruptedException {
		if (vtnConfiguration.getVtnUrl() != null) {
			multiHttpClientConfig.get(vtnConfiguration.getVtnId()).oadrCanceledReport(payload);
		} else if (vtnConfiguration.getVtnXmppHost() != null && vtnConfiguration.getVtnXmppPort() != null) {
			multiXmppClientConfig.get(vtnConfiguration.getVtnId()).oadrCanceledReport(payload);
		}
	}

	public Object oadrPoll(VtnSessionConfiguration vtnConfiguration, OadrPollType event) throws Oadr20bException,
			Oadr20bHttpLayerException, Oadr20bXMLSignatureException, Oadr20bXMLSignatureValidationException {
		if (vtnConfiguration.getVtnUrl() != null) {
			return multiHttpClientConfig.get(vtnConfiguration.getVtnId()).oadrPoll(event);
		}
		return null;
	}

	public void oadrCreatePartyRegistration(VtnSessionConfiguration vtnConfiguration,
			OadrCreatePartyRegistrationType payload) throws Oadr20bException, Oadr20bHttpLayerException,
			Oadr20bXMLSignatureException, Oadr20bXMLSignatureValidationException, XmppStringprepException,
			NotConnectedException, Oadr20bMarshalException, InterruptedException {
		if (vtnConfiguration.getVtnUrl() != null) {
			multiHttpClientConfig.get(vtnConfiguration.getVtnId()).oadrCreatePartyRegistration(payload);
		} else if (vtnConfiguration.getVtnXmppHost() != null && vtnConfiguration.getVtnXmppPort() != null) {
			multiXmppClientConfig.get(vtnConfiguration.getVtnId()).oadrCreatePartyRegistration(payload);
		}
	}

	public void oadrCancelPartyRegistration(VtnSessionConfiguration vtnConfiguration,
			OadrCancelPartyRegistrationType payload) throws Oadr20bException, Oadr20bHttpLayerException,
			Oadr20bXMLSignatureException, Oadr20bXMLSignatureValidationException, XmppStringprepException,
			NotConnectedException, Oadr20bMarshalException, InterruptedException {
		if (vtnConfiguration.getVtnUrl() != null) {
			multiHttpClientConfig.get(vtnConfiguration.getVtnId()).oadrCancelPartyRegistration(payload);
		} else if (vtnConfiguration.getVtnXmppHost() != null && vtnConfiguration.getVtnXmppPort() != null) {
			multiXmppClientConfig.get(vtnConfiguration.getVtnId()).oadrCancelPartyRegistration(payload);
		}
	}

	public void oadrResponseReregisterParty(VtnSessionConfiguration vtnConfiguration, OadrResponseType payload)
			throws Oadr20bException, Oadr20bHttpLayerException, Oadr20bXMLSignatureException,
			Oadr20bXMLSignatureValidationException, XmppStringprepException, NotConnectedException,
			Oadr20bMarshalException, InterruptedException {
		if (vtnConfiguration.getVtnUrl() != null) {
			multiHttpClientConfig.get(vtnConfiguration.getVtnId()).oadrResponseReregisterParty(payload);
		} else if (vtnConfiguration.getVtnXmppHost() != null && vtnConfiguration.getVtnXmppPort() != null) {
			multiXmppClientConfig.get(vtnConfiguration.getVtnId()).oadrResponseReregisterParty(payload);
		}
	}

	public void oadrCanceledPartyRegistrationType(VtnSessionConfiguration vtnConfiguration,
			OadrCanceledPartyRegistrationType payload) throws Oadr20bException, Oadr20bHttpLayerException,
			Oadr20bXMLSignatureException, Oadr20bXMLSignatureValidationException, XmppStringprepException,
			NotConnectedException, Oadr20bMarshalException, InterruptedException {
		if (vtnConfiguration.getVtnUrl() != null) {
			multiHttpClientConfig.get(vtnConfiguration.getVtnId()).oadrCanceledPartyRegistrationType(payload);
		} else if (vtnConfiguration.getVtnXmppHost() != null && vtnConfiguration.getVtnXmppPort() != null) {
			multiXmppClientConfig.get(vtnConfiguration.getVtnId()).oadrCanceledPartyRegistrationType(payload);
		}
	}

	public void oadrCreateOpt(VtnSessionConfiguration vtnConfiguration, OadrCreateOptType payload)
			throws Oadr20bException, Oadr20bHttpLayerException, Oadr20bXMLSignatureException,
			Oadr20bXMLSignatureValidationException, XmppStringprepException, NotConnectedException,
			Oadr20bMarshalException, InterruptedException {
		if (vtnConfiguration.getVtnUrl() != null) {
			multiHttpClientConfig.get(vtnConfiguration.getVtnId()).oadrCreateOpt(payload);
		} else if (vtnConfiguration.getVtnXmppHost() != null && vtnConfiguration.getVtnXmppPort() != null) {
			multiXmppClientConfig.get(vtnConfiguration.getVtnId()).oadrCreateOpt(payload);
		}
	}

	public void oadrCancelOptType(VtnSessionConfiguration vtnConfiguration, OadrCancelOptType payload)
			throws Oadr20bException, Oadr20bHttpLayerException, Oadr20bXMLSignatureException,
			Oadr20bXMLSignatureValidationException, XmppStringprepException, NotConnectedException,
			Oadr20bMarshalException, InterruptedException {
		if (vtnConfiguration.getVtnUrl() != null) {
			multiHttpClientConfig.get(vtnConfiguration.getVtnId()).oadrCancelOptType(payload);
		} else if (vtnConfiguration.getVtnXmppHost() != null && vtnConfiguration.getVtnXmppPort() != null) {
			multiXmppClientConfig.get(vtnConfiguration.getVtnId()).oadrCancelOptType(payload);
		}
	}

	public void oadrCreatedEvent(VtnSessionConfiguration vtnConfiguration, OadrCreatedEventType event)
			throws Oadr20bException, Oadr20bHttpLayerException, Oadr20bXMLSignatureException,
			Oadr20bXMLSignatureValidationException, XmppStringprepException, NotConnectedException,
			Oadr20bMarshalException, InterruptedException {
		if (vtnConfiguration.getVtnUrl() != null) {
			multiHttpClientConfig.get(vtnConfiguration.getVtnId()).oadrCreatedEvent(event);
		} else if (vtnConfiguration.getVtnXmppHost() != null && vtnConfiguration.getVtnXmppPort() != null) {
			multiXmppClientConfig.get(vtnConfiguration.getVtnId()).oadrCreatedEvent(event);
		}
	}

	public void oadrRequestEvent(VtnSessionConfiguration vtnConfiguration, OadrRequestEventType payload)
			throws Oadr20bException, Oadr20bHttpLayerException, Oadr20bXMLSignatureException,
			Oadr20bXMLSignatureValidationException, XmppStringprepException, NotConnectedException,
			Oadr20bMarshalException, InterruptedException {
		if (vtnConfiguration.getVtnUrl() != null) {
			multiHttpClientConfig.get(vtnConfiguration.getVtnId()).oadrRequestEvent(payload);
		} else if (vtnConfiguration.getVtnXmppHost() != null && vtnConfiguration.getVtnXmppPort() != null) {
			multiXmppClientConfig.get(vtnConfiguration.getVtnId()).oadrRequestEvent(payload);
		}
	}

	public void oadrRegisterReport(VtnSessionConfiguration vtnConfiguration, OadrRegisterReportType payload)
			throws Oadr20bException, Oadr20bHttpLayerException, Oadr20bXMLSignatureException,
			Oadr20bXMLSignatureValidationException, XmppStringprepException, NotConnectedException,
			Oadr20bMarshalException, InterruptedException {
		if (vtnConfiguration.getVtnUrl() != null) {
			multiHttpClientConfig.get(vtnConfiguration.getVtnId()).oadrRegisterReport(payload);
		} else if (vtnConfiguration.getVtnXmppHost() != null && vtnConfiguration.getVtnXmppPort() != null) {
			multiXmppClientConfig.get(vtnConfiguration.getVtnId()).oadrRegisterReport(payload);
		}
	}

	public void oadrQueryRegistrationType(VtnSessionConfiguration vtnConfiguration, OadrQueryRegistrationType payload)
			throws Oadr20bException, Oadr20bHttpLayerException, Oadr20bXMLSignatureException,
			Oadr20bXMLSignatureValidationException, XmppStringprepException, NotConnectedException,
			Oadr20bMarshalException, InterruptedException {
		if (vtnConfiguration.getVtnUrl() != null) {
			multiHttpClientConfig.get(vtnConfiguration.getVtnId()).oadrQueryRegistrationType(payload);
		} else if (vtnConfiguration.getVtnXmppHost() != null && vtnConfiguration.getVtnXmppPort() != null) {
			multiXmppClientConfig.get(vtnConfiguration.getVtnId()).oadrQueryRegistrationType(payload);
		}
	}

	public Map<String, OadrXmppVenClient20b> getMultiXmppClientConfig() {
		return multiXmppClientConfig;
	}

	public void setMultiXmppClientConfig(Map<String, OadrXmppVenClient20b> multiXmppClientConfig) {
		this.multiXmppClientConfig = multiXmppClientConfig;
	}

}
