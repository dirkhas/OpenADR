package com.avob.openadr.server.oadr20b.vtn.controller.ei;

import java.security.Principal;

import javax.annotation.Resource;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import com.avob.openadr.model.oadr20b.Oadr20bUrlPath;
import com.avob.openadr.model.oadr20b.exception.Oadr20bApplicationLayerException;
import com.avob.openadr.model.oadr20b.exception.Oadr20bMarshalException;
import com.avob.openadr.model.oadr20b.exception.Oadr20bUnmarshalException;
import com.avob.openadr.model.oadr20b.exception.Oadr20bXMLSignatureException;
import com.avob.openadr.model.oadr20b.exception.Oadr20bXMLSignatureValidationException;
import com.avob.openadr.server.oadr20b.vtn.exception.eievent.Oadr20bCreatedEventApplicationLayerException;
import com.avob.openadr.server.oadr20b.vtn.exception.eievent.Oadr20bRequestEventApplicationLayerException;
import com.avob.openadr.server.oadr20b.vtn.service.Oadr20bVTNEiEventService;

/**
 * Oadr EiEvent service controller
 * 
 * @author bzanni
 *
 */
@Controller
@RequestMapping(Oadr20bUrlPath.OADR_BASE_PATH)
public class Oadr20bVTNEiEventController {

	@Resource
	private Oadr20bVTNEiEventService oadr20aVtnEiEventService;

	/**
	 * Service HTTP Endpoint
	 * 
	 * @param payload
	 * @param principal
	 * @return
	 * @throws Oadr20bUnmarshalException
	 * @throws Oadr20bMarshalException
	 * @throws Oadr20bApplicationLayerException
	 * @throws Oadr20bXMLSignatureValidationException
	 * @throws Oadr20bCreatedEventApplicationLayerException
	 * @throws Oadr20bRequestEventApplicationLayerException
	 * @throws Oadr20bXMLSignatureException
	 */
	@PreAuthorize("hasRole('ROLE_VEN') AND hasRole('ROLE_REGISTERED')")
	@RequestMapping(value = Oadr20bUrlPath.EI_EVENT_SERVICE, method = RequestMethod.POST)
	@ResponseBody
	public String request(@RequestBody String payload, Principal principal)
			throws Oadr20bUnmarshalException, Oadr20bMarshalException, Oadr20bApplicationLayerException,
			Oadr20bXMLSignatureValidationException, Oadr20bCreatedEventApplicationLayerException,
			Oadr20bRequestEventApplicationLayerException, Oadr20bXMLSignatureException {

		return oadr20aVtnEiEventService.request(principal.getName(), payload);

	}

}
