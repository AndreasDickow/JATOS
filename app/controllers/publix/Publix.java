package controllers.publix;

import models.ComponentModel;
import models.StudyModel;
import models.results.ComponentResult;
import models.results.ComponentResult.ComponentState;
import models.results.StudyResult;
import models.results.StudyResult.StudyState;
import models.workers.Worker;
import play.Logger;
import play.libs.F.Function;
import play.libs.F.Promise;
import play.libs.WS;
import play.mvc.Controller;
import play.mvc.Result;
import services.JsonUtils;

import com.fasterxml.jackson.core.JsonProcessingException;

import controllers.ControllerUtils;
import controllers.Users;
import exceptions.ForbiddenReloadException;
import exceptions.PublixException;

/**
 * Abstract controller class for all controller that implement the IPublix
 * interface. It defines common methods and constants.
 * 
 * @author Kristian Lange
 */
public abstract class Publix<T extends Worker> extends Controller implements
		IPublix {

	// ID cookie name and value names
	public static final String ID_COOKIE_NAME = "JATOS_IDS";
	public static final String WORKER_ID = "workerId";
	public static final String STUDY_ID = "studyId";
	public static final String COMPONENT_ID = "componentId";
	public static final String STUDY_RESULT_ID = "studyResultId";
	public static final String COMPONENT_RESULT_ID = "componentResultId";
	public static final String COMPONENT_POSITION = "componentPos";

	private static final String CLASS_NAME = Publix.class.getSimpleName();

	protected PublixUtils<T> utils;

	public Publix(PublixUtils<T> utils) {
		this.utils = utils;
	}

	@Override
	public Promise<Result> startComponent(Long studyId, Long componentId)
			throws PublixException {
		Logger.info(CLASS_NAME + ".startComponent: studyId " + studyId + ", "
				+ "componentId " + componentId + ", " + "workerId "
				+ session(WORKER_ID));

		T worker = utils.retrieveTypedWorker(session(WORKER_ID));
		StudyModel study = utils.retrieveStudy(studyId);
		ComponentModel component = utils.retrieveComponent(study, componentId);
		StudyResult studyResult = utils.retrieveWorkersLastStudyResult(worker,
				study);
		ComponentResult componentResult = null;
		try {
			componentResult = utils.startComponent(component, studyResult);
		} catch (ForbiddenReloadException e) {
			return Promise
					.pure((Result) redirect(controllers.publix.routes.PublixInterceptor
							.finishStudy(studyId, false, e.getMessage())));
		}
		PublixUtils.setIdCookie(studyResult, componentResult, worker);
		String urlPath = StudyAssets.getComponentUrlPath(study.getDirName(),
				component);
		String urlWithQueryStr = StudyAssets
				.getUrlWithRequestQueryString(urlPath);
		return forwardTo(urlWithQueryStr);
	}

	@Override
	public Promise<Result> startComponentByPosition(Long studyId,
			Integer position) throws PublixException {
		Logger.info(CLASS_NAME + ".startComponentByPosition: studyId "
				+ studyId + ", " + "position " + position + ", "
				+ "logged-in user's email " + session(Users.SESSION_EMAIL));
		ComponentModel component = utils.retrieveComponentByPosition(studyId,
				position);
		return startComponent(studyId, component.getId());
	}

	@Override
	public Result startNextComponent(Long studyId) throws PublixException {
		Logger.info(CLASS_NAME + ".startNextComponent: studyId " + studyId
				+ ", " + "workerId " + session(WORKER_ID));
		T worker = utils.retrieveTypedWorker(session(WORKER_ID));
		StudyModel study = utils.retrieveStudy(studyId);
		StudyResult studyResult = utils.retrieveWorkersLastStudyResult(worker,
				study);

		ComponentModel nextComponent = utils
				.retrieveNextActiveComponent(studyResult);
		if (nextComponent == null) {
			// Study has no more components -> finish it
			return redirect(controllers.publix.routes.PublixInterceptor
					.finishStudy(studyId, true, null));
		}
		String urlWithQueryString = StudyAssets
				.getUrlWithRequestQueryString(controllers.publix.routes.PublixInterceptor
						.startComponent(studyId, nextComponent.getId()).url());
		return redirect(urlWithQueryString);
	}

	@Override
	public Result getStudyProperties(Long studyId) throws PublixException,
			JsonProcessingException {
		Logger.info(CLASS_NAME + ".getStudyProperties: studyId " + studyId);
		T worker = utils.retrieveTypedWorker(session(WORKER_ID));
		StudyModel study = utils.retrieveStudy(studyId);
		utils.checkWorkerAllowedToDoStudy(worker, study);
		StudyResult studyResult = utils.retrieveWorkersLastStudyResult(worker,
				study);
		studyResult.setStudyState(StudyState.DATA_RETRIEVED);
		studyResult.merge();
		return ok(JsonUtils.asJsonForPublix(study));
	}

	@Override
	public Result getStudySessionData(Long studyId) throws PublixException {
		Logger.info(CLASS_NAME + ".getStudySessionData: studyId " + studyId);
		T worker = utils.retrieveTypedWorker(session(WORKER_ID));
		StudyModel study = utils.retrieveStudy(studyId);
		utils.checkWorkerAllowedToDoStudy(worker, study);
		StudyResult studyResult = utils.retrieveWorkersLastStudyResult(worker,
				study);
		String studySessionData = studyResult.getStudySessionData();
		return ok(studySessionData);
	}

	@Override
	public Result setStudySessionData(Long studyId) throws PublixException {
		Logger.info(CLASS_NAME + ".setStudySessionData: studyId " + studyId);
		T worker = utils.retrieveTypedWorker(session(WORKER_ID));
		StudyModel study = utils.retrieveStudy(studyId);
		utils.checkWorkerAllowedToDoStudy(worker, study);
		StudyResult studyResult = utils.retrieveWorkersLastStudyResult(worker,
				study);
		String studySessionData = utils
				.getDataFromRequestBody(request().body());
		studyResult.setStudySessionData(studySessionData);
		studyResult.merge();
		return ok();
	}

	@Override
	public Result getComponentProperties(Long studyId, Long componentId)
			throws PublixException, JsonProcessingException {
		Logger.info(CLASS_NAME + ".getComponentProperties: studyId " + studyId
				+ ", " + "componentId " + componentId);
		T worker = utils.retrieveTypedWorker(session(WORKER_ID));
		StudyModel study = utils.retrieveStudy(studyId);
		ComponentModel component = utils.retrieveComponent(study, componentId);
		utils.checkWorkerAllowedToDoStudy(worker, study);
		utils.checkComponentBelongsToStudy(study, component);

		StudyResult studyResult = utils.retrieveWorkersLastStudyResult(worker,
				study);
		ComponentState maxAllowedComponentState = ComponentState.STARTED;
		ComponentResult componentResult;
		try {
			componentResult = utils.retrieveStartedComponentResult(component,
					studyResult, maxAllowedComponentState);
		} catch (ForbiddenReloadException e) {
			return redirect(controllers.publix.routes.PublixInterceptor
					.finishStudy(studyId, false, e.getMessage()));
		}

		componentResult.setComponentState(ComponentState.DATA_RETRIEVED);
		componentResult.merge();
		return ok(JsonUtils.asJsonForPublix(component));
	}

	@Override
	public Result submitResultData(Long studyId, Long componentId)
			throws PublixException {
		Logger.info(CLASS_NAME + ".submitResultData: studyId " + studyId + ", "
				+ "componentId " + componentId);
		StudyModel study = utils.retrieveStudy(studyId);
		T worker = utils.retrieveTypedWorker(session(WORKER_ID));
		ComponentModel component = utils.retrieveComponent(study, componentId);
		utils.checkWorkerAllowedToDoStudy(worker, study);
		utils.checkComponentBelongsToStudy(study, component);

		StudyResult studyResult = utils.retrieveWorkersLastStudyResult(worker,
				study);
		ComponentState maxAllowedComponentState = ComponentState.DATA_RETRIEVED;
		ComponentResult componentResult;
		try {
			componentResult = utils.retrieveStartedComponentResult(component,
					studyResult, maxAllowedComponentState);
		} catch (ForbiddenReloadException e) {
			return redirect(controllers.publix.routes.PublixInterceptor
					.finishStudy(studyId, false, e.getMessage()));
		}

		String resultData = utils.getDataFromRequestBody(request().body());
		componentResult.setData(resultData);
		componentResult.setComponentState(ComponentState.RESULTDATA_POSTED);
		componentResult.merge();
		return ok();
	}

	@Override
	public Result finishComponent(Long studyId, Long componentId,
			Boolean successful, String errorMsg) throws PublixException {
		Logger.info(CLASS_NAME + ".finishComponent: studyId " + studyId + ", "
				+ "componentId " + componentId + ", " + "logged-in user email "
				+ session(Users.SESSION_EMAIL) + ", " + "successful "
				+ successful + ", " + "errorMsg \"" + errorMsg + "\"");
		StudyModel study = utils.retrieveStudy(studyId);
		T worker = utils.retrieveTypedWorker(session(WORKER_ID));
		ComponentModel component = utils.retrieveComponent(study, componentId);
		utils.checkWorkerAllowedToDoStudy(worker, study);
		utils.checkComponentBelongsToStudy(study, component);

		StudyResult studyResult = utils.retrieveWorkersLastStudyResult(worker,
				study);
		ComponentResult componentResult = utils
				.retrieveCurrentComponentResult(studyResult);

		if (successful) {
			componentResult.setComponentState(ComponentState.FINISHED);
			componentResult.setErrorMsg(errorMsg);
		} else {
			componentResult.setComponentState(ComponentState.FAIL);
			componentResult.setErrorMsg(errorMsg);
		}
		componentResult.merge();
		return ok();
	}

	@Override
	public Result abortStudy(Long studyId, String message)
			throws PublixException {
		Logger.info(CLASS_NAME + ".abortStudy: studyId " + studyId + ", "
				+ "logged-in user email " + session(Users.SESSION_EMAIL) + ", "
				+ "message \"" + message + "\"");
		StudyModel study = utils.retrieveStudy(studyId);
		T worker = utils.retrieveTypedWorker(session(WORKER_ID));
		utils.checkWorkerAllowedToDoStudy(worker, study);

		StudyResult studyResult = utils.retrieveWorkersLastStudyResult(worker,
				study);
		if (!utils.studyDone(studyResult)) {
			utils.abortStudy(message, studyResult);
		}

		PublixUtils.discardIdCookie();
		if (ControllerUtils.isAjax()) {
			return ok();
		} else {
			return ok(views.html.publix.abort.render());
		}
	}

	@Override
	public Result finishStudy(Long studyId, Boolean successful, String errorMsg)
			throws PublixException {
		Logger.info(CLASS_NAME + ".finishStudy: studyId " + studyId + ", "
				+ "workerId " + session(WORKER_ID) + ", " + "successful "
				+ successful + ", " + "errorMsg \"" + errorMsg + "\"");
		StudyModel study = utils.retrieveStudy(studyId);
		T worker = utils.retrieveTypedWorker(session(WORKER_ID));
		utils.checkWorkerAllowedToDoStudy(worker, study);

		StudyResult studyResult = utils.retrieveWorkersLastStudyResult(worker,
				study);
		if (!utils.studyDone(studyResult)) {
			utils.finishStudy(successful, errorMsg, studyResult);
		}

		PublixUtils.discardIdCookie();
		if (ControllerUtils.isAjax()) {
			return ok();
		} else {
			if (!successful) {
				return ok(views.html.publix.error.render(errorMsg));
			} else {
				return ok(views.html.publix.finishedAndThanks.render());
			}
		}
	}

	@Override
	public Result logError(Long studyId, Long componentId) {
		String msg = request().body().asText();
		Logger.error(CLASS_NAME + " - logging component script error: studyId "
				+ studyId + ", " + "componentId " + componentId + ", "
				+ "error message \"" + msg + "\".");
		return ok();
	}

	/**
	 * Gets the value of to the given key in request's query string and trims
	 * whitespace.
	 */
	public static String getQueryString(String key) {
		String value = request().getQueryString(key);
		if (value != null) {
			value = value.trim();
		}
		return value;
	}

	/**
	 * Like an internal redirect or an proxy. The URL in the browser doesn't
	 * change.
	 */
	public static Promise<Result> forwardTo(String url) {
		Promise<WS.Response> response = WS.url(url).get();
		return response.map(new Function<WS.Response, Result>() {
			public Result apply(WS.Response response) {
				// Prevent browser from caching pages - this would be an
				// security issue and additionally confuse the study flow
				response().setHeader("Cache-control", "no-cache, no-store");
				return ok(response.getBody()).as("text/html");
			}
		});
	}

}
