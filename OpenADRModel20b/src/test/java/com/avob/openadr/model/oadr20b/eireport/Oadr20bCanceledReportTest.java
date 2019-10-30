package com.avob.openadr.model.oadr20b.eireport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;

import javax.xml.bind.JAXBException;
import javax.xml.datatype.DatatypeConfigurationException;

import org.assertj.core.util.Files;
import org.junit.Test;

import com.avob.openadr.model.oadr20b.Oadr20bFactory;
import com.avob.openadr.model.oadr20b.Oadr20bJAXBContext;
import com.avob.openadr.model.oadr20b.builders.Oadr20bEiReportBuilders;
import com.avob.openadr.model.oadr20b.exception.Oadr20bMarshalException;
import com.avob.openadr.model.oadr20b.exception.Oadr20bUnmarshalException;
import com.avob.openadr.model.oadr20b.oadr.OadrCanceledReportType;

public class Oadr20bCanceledReportTest {

	private Oadr20bJAXBContext jaxbContext;

	public Oadr20bCanceledReportTest() throws JAXBException {
		jaxbContext = Oadr20bJAXBContext.getInstance();
	}

	@Test
	public void unvalidatingMarshalUnmarshalTest() throws DatatypeConfigurationException {

		String requestId = null;
		int responseCode = 200;
		String venId = "venId";
		OadrCanceledReportType request = Oadr20bEiReportBuilders
				.newOadr20bCanceledReportBuilder(requestId, responseCode, venId).build();

		boolean assertion = false;
		try {
			jaxbContext.marshalRoot(request, true);
		} catch (Oadr20bMarshalException e) {
			assertion = true;
		}

		assertTrue(assertion);

		File file = new File("src/test/resources/eireport/unvalidatingOadrCanceledReport.xml");
		assertion = false;
		try {
			jaxbContext.unmarshal(file, OadrCanceledReportType.class);
		} catch (Oadr20bUnmarshalException e) {
			assertion = true;
		}
		assertTrue(assertion);

	}

	@Test
	public void unmarshalMarshalValidatingTest() throws Oadr20bUnmarshalException, Oadr20bMarshalException {
		File file = new File("src/test/resources/eireport/oadrCanceledReport.xml");
		OadrCanceledReportType unmarshal = jaxbContext.unmarshal(file, OadrCanceledReportType.class);
		assertEquals("000", unmarshal.getEiResponse().getResponseCode());
		assertEquals("String", unmarshal.getEiResponse().getRequestID());
		assertEquals("String", unmarshal.getEiResponse().getResponseDescription());
		assertEquals(2, unmarshal.getOadrPendingReports().getReportRequestID().size());
		assertEquals("REP_09876", unmarshal.getOadrPendingReports().getReportRequestID().get(0));
		assertEquals("REP_09877", unmarshal.getOadrPendingReports().getReportRequestID().get(1));

		File file2 = new File("src/test/resources/eireport/genOadrCanceledReport.xml");
		jaxbContext.marshal(Oadr20bFactory.createOadrCanceledReport(unmarshal), file2);
		assertTrue(file2.exists());
		Files.delete(file2);

	}

}
