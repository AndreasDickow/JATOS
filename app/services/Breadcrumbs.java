package services;

import java.util.LinkedHashMap;
import java.util.Map;

import models.ComponentModel;
import models.StudyModel;
import models.UserModel;
import models.workers.Worker;
import play.mvc.Call;
import controllers.routes;

public class Breadcrumbs {

	private Map<String, String> breadcrumbs = new LinkedHashMap<>();

	public Map<String, String> getBreadcrumbs() {
		return breadcrumbs;
	}

	public Breadcrumbs put(String name, String url) {
		this.breadcrumbs.put(name, url);
		return this;
	}

	public Breadcrumbs put(String name, Call call) {
		this.breadcrumbs.put(name, call.url());
		return this;
	}

	public static Breadcrumbs generateForHome(String last) {
		services.Breadcrumbs breadcrumbs = new services.Breadcrumbs().put(
				"Home", routes.Home.home()).put(last, "");
		return breadcrumbs;
	}

	public static Breadcrumbs generateForUser(UserModel user, String last) {
		services.Breadcrumbs breadcrumbs = new services.Breadcrumbs()
				.put("Home", routes.Home.home())
				.put(user.toString(), routes.Users.profile(user.getEmail()))
				.put(last, "");
		return breadcrumbs;
	}

	public static Breadcrumbs generateForStudy(StudyModel study, String last) {
		services.Breadcrumbs breadcrumbs = new services.Breadcrumbs()
				.put("Home", routes.Home.home())
				.put(study.getTitle(),
						routes.Studies.index(study.getId(), null))
				.put(last, "");
		return breadcrumbs;
	}

	public static Breadcrumbs generateForWorkerResult(Worker worker,
			String last) {
		services.Breadcrumbs breadcrumbs = new services.Breadcrumbs()
				.put("Home", routes.Home.home())
				.put("Worker " + worker.getId(), "")
				.put("Results", routes.Workers.index(worker.getId()))
				.put(last, "");
		return breadcrumbs;
	}
	
	public static Breadcrumbs generateForStudyResult(StudyModel study,
			String last) {
		services.Breadcrumbs breadcrumbs = new services.Breadcrumbs()
				.put("Home", routes.Home.home())
				.put(study.getTitle(),
						routes.Studies.index(study.getId(), null))
				.put("Results", routes.StudyResults.index(study.getId()))
				.put(last, "");
		return breadcrumbs;
	}

	public static Breadcrumbs generateForComponent(StudyModel study,
			ComponentModel component, String last) {
		Breadcrumbs breadcrumbs = new Breadcrumbs()
				.put("Home", routes.Home.home())
				.put(study.getTitle(),
						routes.Studies.index(study.getId(), null))
				.put(component.getTitle(), "").put(last, "");
		return breadcrumbs;
	}

	public static Breadcrumbs generateForComponentResult(StudyModel study,
			ComponentModel component, String last) {
		services.Breadcrumbs breadcrumbs = new services.Breadcrumbs()
				.put("Home", routes.Home.home())
				.put(study.getTitle(),
						routes.Studies.index(study.getId(), null))
				.put(component.getTitle(), "")
				.put("Results",
						routes.ComponentResults.index(study.getId(),
								component.getId())).put(last, "");
		return breadcrumbs;
	}

}
