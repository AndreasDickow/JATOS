package models.common;

import general.common.MessagesStrings;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.ManyToMany;
import javax.persistence.OneToOne;
import javax.persistence.Table;

import models.common.workers.JatosWorker;

import org.jsoup.Jsoup;
import org.jsoup.safety.Whitelist;

import play.data.validation.ValidationError;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Model and DB entity of a user.
 * 
 * @author Kristian Lange
 */
@Entity
@Table(name = "User")
public class User {

	public static final String NAME = "name";
	public static final String EMAIL = "email";
	public static final String PASSWORD = "password";
	public static final String PASSWORD_REPEAT = "passwordRepeat";
	public static final String OLD_PASSWORD = "oldPassword";

	@Id
	private String email;

	private String name;

	@JsonIgnore
	@OneToOne(mappedBy = "user", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
	private JatosWorker worker;

	// Password is stored as a hash
	@JsonIgnore
	private String passwordHash;

	@ManyToMany(mappedBy = "userList", fetch = FetchType.LAZY)
	private Set<Study> studyList = new HashSet<>();

	public User(String email, String name, String passwordHash) {
		this.email = email;
		this.name = name;
		this.passwordHash = passwordHash;
	}

	public User() {
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getEmail() {
		return this.email;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getName() {
		return this.name;
	}

	public void setPasswordHash(String passwordHash) {
		this.passwordHash = passwordHash;
	}

	public String getPasswordHash() {
		return this.passwordHash;
	}

	public void setStudyList(Set<Study> studyList) {
		this.studyList = studyList;
	}

	public Set<Study> getStudyList() {
		return this.studyList;
	}

	public void setWorker(JatosWorker worker) {
		this.worker = worker;
	}

	public JatosWorker getWorker() {
		return this.worker;
	}

	@Override
	public String toString() {
		if (name != null && !name.trim().isEmpty()) {
			return name + " (" + email + ")";
		} else {
			return email;
		}
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((email == null) ? 0 : email.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (!(obj instanceof User)) {
			return false;
		}
		User other = (User) obj;
		if (email == null) {
			if (other.getEmail() != null) {
				return false;
			}
		} else if (!email.equals(other.getEmail())) {
			return false;
		}
		return true;
	}

	public List<ValidationError> validate() {
		List<ValidationError> errorList = new ArrayList<>();
		if (email == null || email.trim().isEmpty()) {
			errorList.add(new ValidationError(EMAIL,
					MessagesStrings.MISSING_EMAIL));
		}
		if (email != null && !Jsoup.isValid(email, Whitelist.none())) {
			errorList.add(new ValidationError(EMAIL,
					MessagesStrings.NO_HTML_ALLOWED));
		}
		if (name == null || name.trim().isEmpty()) {
			errorList
					.add(new ValidationError(NAME, MessagesStrings.MISSING_NAME));
		}
		if (name != null && !Jsoup.isValid(name, Whitelist.none())) {
			errorList.add(new ValidationError(NAME,
					MessagesStrings.NO_HTML_ALLOWED));
		}
		return errorList.isEmpty() ? null : errorList;
	}

}