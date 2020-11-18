package com.avob.openadr.dummy;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Resource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.avob.openadr.model.oadr20b.Oadr20bFactory;
import com.avob.openadr.model.oadr20b.builders.Oadr20bEiBuilders;
import com.avob.openadr.model.oadr20b.builders.Oadr20bEiReportBuilders;
import com.avob.openadr.model.oadr20b.builders.eireport.Oadr20bUpdateReportBuilder;
import com.avob.openadr.model.oadr20b.builders.eireport.Oadr20bUpdateReportOadrReportBuilder;
import com.avob.openadr.model.oadr20b.ei.IntervalType;
import com.avob.openadr.model.oadr20b.ei.ReportNameEnumeratedType;
import com.avob.openadr.model.oadr20b.oadr.OadrCancelReportType;
import com.avob.openadr.model.oadr20b.oadr.OadrCreateReportType;
import com.avob.openadr.model.oadr20b.oadr.OadrReportRequestType;
import com.avob.openadr.model.oadr20b.oadr.OadrUpdateReportType;
import com.avob.openadr.model.oadr20b.xcal.DurationPropType;
import com.avob.openadr.server.oadr20b.ven.MultiVtnConfig;
import com.avob.openadr.server.oadr20b.ven.VtnSessionConfiguration;
import com.avob.openadr.server.oadr20b.ven.service.Oadr20bVENEiReportService;

@Service
public class RequestedReportSimulator {

	private static final Logger LOGGER = LoggerFactory.getLogger(DummyVEN20bEiReportListener.class);

	private static final Object METADATA_REPORT_SPECIFIER_ID = "METADATA";

	@Resource
	private MultiVtnConfig multiVtnConfig;

	@Resource
	private Oadr20bVENEiReportService oadr20bVENEiReportService;

	private AtomicInteger currentValue = new AtomicInteger(Float.floatToIntBits(-1F));

	private Map<String, Map<String, Map<String, OadrReportRequestType>>> requestedReport = new ConcurrentHashMap<>();

	private ScheduledExecutorService executor = Executors.newScheduledThreadPool(5);
	private Map<String, Map<String, Map<String, ScheduledFuture<?>>>> simulateReadingTasks = new ConcurrentHashMap<>();
	private Map<String, Map<String, ScheduledFuture<?>>> reportBackTasks = new ConcurrentHashMap<>();
	private Map<String, Map<String, Map<String, Map<String, TreeMap<Long, Float>>>>> simulateReadingBuffer = new ConcurrentHashMap<>();
	private Map<String, VtnSessionConfiguration> vtnSessionConfig = new ConcurrentHashMap<>();

	public Float getCurrentValue() {
		return Float.intBitsToFloat(currentValue.get());
	}

	public void setCurrentValue(Float currentValue) {
		this.currentValue.set(Float.floatToIntBits(currentValue));
	}

	public void create(VtnSessionConfiguration vtnConfig, OadrCreateReportType oadrCreateReportType) {
		for (OadrReportRequestType oadrReportRequestType : oadrCreateReportType.getOadrReportRequest()) {
			String reportRequestID = oadrReportRequestType.getReportRequestID();
			String reportSpecifierID = oadrReportRequestType.getReportSpecifier().getReportSpecifierID();

			if (!METADATA_REPORT_SPECIFIER_ID.equals(reportSpecifierID)) {
				Map<String, Map<String, OadrReportRequestType>> vtnMap = requestedReport.get(vtnConfig.getSessionKey());
				if (vtnMap == null) {
					vtnMap = new ConcurrentHashMap<>();
				}
				Map<String, OadrReportRequestType> reportRequestMap = vtnMap.get(reportRequestID);
				if (reportRequestMap == null) {
					reportRequestMap = new ConcurrentHashMap<>();
				}
				reportRequestMap.put(reportSpecifierID, oadrReportRequestType);
				vtnMap.put(reportRequestID, reportRequestMap);
				requestedReport.put(vtnConfig.getSessionKey(), vtnMap);
				vtnSessionConfig.put(vtnConfig.getSessionKey(), vtnConfig);
			}
		}

		this.refreshSimulator();
	}

	public void cancel(VtnSessionConfiguration vtnConfig, OadrCancelReportType oadrCancelReportType) {
		oadrCancelReportType.getReportRequestID().forEach(reportRequestId -> {
			if (requestedReport.get(vtnConfig.getVtnId()) != null) {
				requestedReport.get(vtnConfig.getVtnId()).remove(reportRequestId);
			}
			Map<String, ScheduledFuture<?>> map = simulateReadingTasks.get(vtnConfig.getVtnId()).get(reportRequestId);
			if (simulateReadingTasks.get(vtnConfig.getVtnId()) != null && map != null) {
				map.forEach((reportSpecifierId, schedule) -> {
					schedule.cancel(false);
				});
				simulateReadingTasks.get(vtnConfig.getVtnId()).remove(reportRequestId);
			}
			if (reportBackTasks.get(vtnConfig.getVtnId()) != null
					&& reportBackTasks.get(vtnConfig.getVtnId()).get(reportRequestId) != null) {
				reportBackTasks.get(vtnConfig.getVtnId()).get(reportRequestId).cancel(false);
				reportBackTasks.get(vtnConfig.getVtnId()).remove(reportRequestId);
			}
			if (simulateReadingBuffer.get(vtnConfig.getVtnId()) != null
					&& simulateReadingBuffer.get(vtnConfig.getVtnId()).get(reportRequestId) != null) {
				simulateReadingBuffer.get(vtnConfig.getVtnId()).remove(reportRequestId);
			}

		});

		this.refreshSimulator();
	}

	private void refreshSimulator() {

		OffsetDateTime start = OffsetDateTime.now().truncatedTo(ChronoUnit.MINUTES).plusMinutes(1);

		requestedReport.forEach((sessionKey, vtns) -> {
			vtns.forEach((reportRequestId, specifiers) -> {
				specifiers.forEach((reportSpecifierId, reportRequest) -> {

					DurationPropType reportBackDuration = reportRequest.getReportSpecifier().getReportBackDuration();
					DurationPropType granularity = reportRequest.getReportSpecifier().getGranularity();
					Long reportBackDurationToMillisecond = Oadr20bFactory
							.xmlDurationToMillisecond(reportBackDuration.getDuration());

					OffsetDateTime reportBackStart = start.plus(reportBackDurationToMillisecond, ChronoUnit.MILLIS);
					ReportBackTask reportBackTask = new ReportBackTask(reportBackStart, reportBackDuration, granularity,
							sessionKey, reportRequestId);
					ScheduledFuture<?> scheduleReportBack = executor.schedule(reportBackTask,
							reportBackDurationToMillisecond, TimeUnit.MILLISECONDS);

					Map<String, ScheduledFuture<?>> reportBackTaskVtn = reportBackTasks.get(sessionKey);
					if (reportBackTaskVtn == null) {
						reportBackTaskVtn = new ConcurrentHashMap<>();
					}
					reportBackTaskVtn.put(reportRequestId, scheduleReportBack);
					reportBackTasks.put(sessionKey, reportBackTaskVtn);

					reportRequest.getReportSpecifier().getSpecifierPayload().forEach(specifier -> {
						String rid = specifier.getRID();
						Long granularityToMillisecond = Oadr20bFactory
								.xmlDurationToMillisecond(granularity.getDuration());
						SimulateRidReadingTask simulateRidReadingTask = new SimulateRidReadingTask(start, granularity,
								sessionKey, reportRequestId, reportSpecifierId, rid);
						ScheduledFuture<?> scheduleGranularity = executor.schedule(simulateRidReadingTask,
								granularityToMillisecond, TimeUnit.MILLISECONDS);

						Map<String, Map<String, ScheduledFuture<?>>> granularityTaskVtn = simulateReadingTasks
								.get(sessionKey);
						if (granularityTaskVtn == null) {
							granularityTaskVtn = new ConcurrentHashMap<>();
						}
						Map<String, ScheduledFuture<?>> map = granularityTaskVtn.get(reportRequestId);
						if (map == null) {
							map = new ConcurrentHashMap<>();
						}
						map.put(reportSpecifierId, scheduleGranularity);
						granularityTaskVtn.put(reportRequestId, map);
						simulateReadingTasks.put(sessionKey, granularityTaskVtn);

					});
				});
			});
		});

	}

	public List<String> getPendingRequestReport() {
		return new ArrayList<>(requestedReport.keySet());
	}

	private class ReportBackTask implements Runnable {

		private String sessionKey;
		private String reportRequestId;
		private OffsetDateTime start;
		private DurationPropType reportBackDuration;
		private DurationPropType granularity;

		public ReportBackTask(OffsetDateTime start, DurationPropType reportBackDuration, DurationPropType granularity,
				String sessionKey, String reportRequestId) {
			this.reportRequestId = reportRequestId;
			this.reportBackDuration = reportBackDuration;
			this.granularity = granularity;
			this.start = start;
			this.sessionKey = sessionKey;
		}

		@Override
		public void run() {

			ScheduledFuture<?> scheduledFuture = reportBackTasks.get(sessionKey).get(reportRequestId);

			if (scheduledFuture.isCancelled()) {
				LOGGER.info(String.format("Cancel report back: %s %s", sessionKey, reportRequestId));
				reportBackTasks.get(sessionKey).remove(reportRequestId);
				return;
			}

			LOGGER.info(String.format("Report back %s %s", sessionKey, reportRequestId));

			String reportId = "0";
			String requestId = "0";
			Long confidence = 1L;
			Float accuracy = 1F;

			ReportNameEnumeratedType reportName = null;
			long createdTimestamp = System.currentTimeMillis();
			Long startTimestamp = null;
			String duration = null;

			Oadr20bUpdateReportBuilder newOadr20bUpdateReportBuilder = Oadr20bEiReportBuilders
					.newOadr20bUpdateReportBuilder(requestId, multiVtnConfig.getMultiConfig(sessionKey).getVenId());

			simulateReadingBuffer.get(sessionKey).get(reportRequestId).forEach((reportSpecifierId, ridMap) -> {

				Oadr20bUpdateReportOadrReportBuilder newOadr20bUpdateReportOadrReportBuilder = Oadr20bEiReportBuilders
						.newOadr20bUpdateReportOadrReportBuilder(reportId, reportSpecifierId, reportRequestId,
								reportName, createdTimestamp, startTimestamp, duration);

				ridMap.forEach((rid, intervals) -> {

					int i = 0;
					for (Entry<Long, Float> entry : intervals.entrySet()) {
						IntervalType interval = Oadr20bEiBuilders
								.newOadr20bReportIntervalTypeBuilder(String.valueOf(i++), entry.getKey(),
										granularity.getDuration(), rid, confidence, accuracy, entry.getValue())
								.build();

						newOadr20bUpdateReportOadrReportBuilder.addInterval(interval);
					}

				});

				newOadr20bUpdateReportBuilder.addReport(newOadr20bUpdateReportOadrReportBuilder.build());

			});

			simulateReadingBuffer.get(sessionKey).get(reportRequestId).clear();

			OadrUpdateReportType build = newOadr20bUpdateReportBuilder.build();

			VtnSessionConfiguration vtnSessionConfiguration = vtnSessionConfig.get(sessionKey);

			oadr20bVENEiReportService.updateReport(vtnSessionConfiguration, build);

			Long reportBackDurationToMillisecond = Oadr20bFactory
					.xmlDurationToMillisecond(reportBackDuration.getDuration());
			OffsetDateTime plus = start.plus(java.time.Duration.ofMillis(reportBackDurationToMillisecond));

			ReportBackTask reportBackTask = new ReportBackTask(plus, reportBackDuration, reportBackDuration, sessionKey,
					reportRequestId);
			ScheduledFuture<?> scheduleReportBack = executor.schedule(reportBackTask, reportBackDurationToMillisecond,
					TimeUnit.MILLISECONDS);

			Map<String, ScheduledFuture<?>> reportBackTaskVtn = reportBackTasks.get(sessionKey);
			if (reportBackTaskVtn == null) {
				reportBackTaskVtn = new ConcurrentHashMap<>();
			}
			reportBackTaskVtn.put(reportRequestId, scheduleReportBack);
			reportBackTasks.put(sessionKey, reportBackTaskVtn);

		}

	}

	private class SimulateRidReadingTask implements Runnable {

		private String reportRequestId;
		private String reportSpecifierId;
		private String rid;
		private DurationPropType granularity;
		private OffsetDateTime start;
		private String sessionKey;

		public SimulateRidReadingTask(OffsetDateTime start, DurationPropType granularity, String sessionKey,
				String reportRequestId, String reportSpecifierId, String rid) {
			this.start = start;
			this.granularity = granularity;
			this.reportRequestId = reportRequestId;
			this.reportSpecifierId = reportSpecifierId;
			this.rid = rid;
			this.sessionKey = sessionKey;
		}

		@Override
		public void run() {

			ScheduledFuture<?> scheduledFuture = simulateReadingTasks.get(sessionKey).get(reportRequestId)
					.get(reportSpecifierId);

			if (scheduledFuture.isCancelled()) {
				LOGGER.info(String.format("Cancel reading: %s %s %s %s", sessionKey, reportRequestId, reportSpecifierId,
						rid));
				simulateReadingTasks.get(sessionKey).get(reportRequestId).remove(reportSpecifierId);
				return;
			}

			LOGGER.info(String.format("Simulate reading: %s %s %s %s", sessionKey, reportRequestId, reportSpecifierId,
					rid));

			Map<String, Map<String, Map<String, TreeMap<Long, Float>>>> vtnMap = simulateReadingBuffer.get(sessionKey);
			if (vtnMap == null) {
				vtnMap = new ConcurrentHashMap<>();
			}
			Map<String, Map<String, TreeMap<Long, Float>>> reportRequestMap = vtnMap.get(reportRequestId);
			if (reportRequestMap == null) {
				reportRequestMap = new ConcurrentHashMap<>();
			}
			Map<String, TreeMap<Long, Float>> reportSpecifierMap = reportRequestMap.get(reportSpecifierId);
			if (reportSpecifierMap == null) {
				reportSpecifierMap = new ConcurrentHashMap<>();
			}
			TreeMap<Long, Float> ridMap = reportSpecifierMap.get(rid);
			if (ridMap == null) {
				ridMap = new TreeMap<>();
			}
			ridMap.put(start.toInstant().toEpochMilli(), getCurrentValue());
			reportSpecifierMap.put(rid, ridMap);
			reportRequestMap.put(reportSpecifierId, reportSpecifierMap);
			vtnMap.put(reportRequestId, reportRequestMap);
			simulateReadingBuffer.put(sessionKey, vtnMap);

			Long granularityToMillisecond = Oadr20bFactory.xmlDurationToMillisecond(granularity.getDuration());
			OffsetDateTime plus = start.plus(java.time.Duration.ofMillis(granularityToMillisecond));

			SimulateRidReadingTask simulateRidReadingTask = new SimulateRidReadingTask(plus, granularity, sessionKey,
					reportRequestId, reportSpecifierId, rid);
			ScheduledFuture<?> schedule = executor.schedule(simulateRidReadingTask, granularityToMillisecond,
					TimeUnit.MILLISECONDS);

			Map<String, Map<String, ScheduledFuture<?>>> simulateReadingTasksVtn = simulateReadingTasks.get(sessionKey);
			if (simulateReadingTasksVtn == null) {
				simulateReadingTasksVtn = new ConcurrentHashMap<>();
			}
			Map<String, ScheduledFuture<?>> map = simulateReadingTasksVtn.get(reportRequestId);
			if (map == null) {
				map = new ConcurrentHashMap<>();
			}
			map.put(reportSpecifierId, schedule);
			simulateReadingTasksVtn.put(reportRequestId, map);
			simulateReadingTasks.put(sessionKey, simulateReadingTasksVtn);

		}

	}

}
