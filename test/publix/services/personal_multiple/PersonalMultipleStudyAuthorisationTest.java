package publix.services.personal_multiple;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;

import models.StudyModel;
import models.StudyResult.StudyState;
import models.workers.PersonalMultipleWorker;

import org.junit.Test;

import publix.exceptions.ForbiddenPublixException;

import common.AbstractTest;
import common.Global;

/**
 * @author Kristian Lange
 */
public class PersonalMultipleStudyAuthorisationTest extends AbstractTest {

	private PersonalMultipleStudyAuthorisation studyAuthorisation;

	@Override
	public void before() throws Exception {
		studyAuthorisation = Global.INJECTOR
				.getInstance(PersonalMultipleStudyAuthorisation.class);
	}

	@Override
	public void after() throws Exception {
	}

	@Test
	public void checkWorkerAllowedToDoStudy() throws NoSuchAlgorithmException,
			IOException, ForbiddenPublixException {
		StudyModel study = importExampleStudy();
		study.addAllowedWorker(PersonalMultipleWorker.WORKER_TYPE);
		addStudy(study);
		PersonalMultipleWorker worker = new PersonalMultipleWorker();
		addWorker(worker);

		studyAuthorisation.checkWorkerAllowedToDoStudy(worker, study);

		// Clean-up
		removeStudy(study);
	}

	@Test
	public void checkWorkerAllowedToDoStudyFinishedStudy()
			throws NoSuchAlgorithmException, IOException,
			ForbiddenPublixException {
		StudyModel study = importExampleStudy();
		study.addAllowedWorker(PersonalMultipleWorker.WORKER_TYPE);
		addStudy(study);
		PersonalMultipleWorker worker = new PersonalMultipleWorker();
		addWorker(worker);

		// State of study has no influence. Personal multiple workers can do
		// studies multiple times
		addStudyResult(study, worker, StudyState.FINISHED);
		studyAuthorisation.checkWorkerAllowedToDoStudy(worker, study);

		addStudyResult(study, worker, StudyState.FINISHED);

		// Clean-up
		removeStudy(study);
	}

}
