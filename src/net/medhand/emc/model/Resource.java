package net.medhand.emc.model;

import java.util.List;

public class Resource {
	
	List<ActiveIngredients> ActiveIngredients;
	Company Company;
	int Id;
	String Title;
	String Type;
	String State;
	String EmcUrl;
	String AuthorisationDate;
	String RetiredDate;
	String LastModifiedDate;
	String Cpn;
	String Content;
	
	public List<ActiveIngredients> getActiveIngredients() {
		return ActiveIngredients;
	}
	public void setActiveIngredients(List<ActiveIngredients> activeIngredients) {
		this.ActiveIngredients = activeIngredients;
	}
	public Company getCompany() {
		return Company;
	}
	public void setCompany(Company company) {
		this.Company = company;
	}
	public int getId() {
		return Id;
	}
	public void setId(int id) {
		Id = id;
	}
	public String getTitle() {
		return Title;
	}
	public void setTitle(String title) {
		Title = title;
	}
	public String getType() {
		return Type;
	}
	public void setType(String type) {
		this.Type = type;
	}
	public String getState() {
		return State;
	}
	public void setState(String state) {
		State = state;
	}
	public String getEmcUrl() {
		return EmcUrl;
	}
	public void setEmcUrl(String emcUrl) {
		EmcUrl = emcUrl;
	}
	public String getAuthorisationDate() {
		return AuthorisationDate;
	}
	public void setAuthorisationDate(String authorisationDate) {
		AuthorisationDate = authorisationDate;
	}
	public String getRetiredDate() {
		return RetiredDate;
	}
	public void setRetiredDate(String retiredDate) {
		RetiredDate = retiredDate;
	}
	public String getLastModifiedDate() {
		return LastModifiedDate;
	}
	public void setLastModifiedDate(String lastModifiedDate) {
		LastModifiedDate = lastModifiedDate;
	}
	public String getCpn() {
		return Cpn;
	}
	public void setCpn(String cpn) {
		Cpn = cpn;
	}
	public String getContent() {
		return Content;
	}
	public void setContent(String content) {
		Content = content;
	}
	
}
