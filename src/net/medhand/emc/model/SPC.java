package net.medhand.emc.model;

public class SPC {
	int Limit;
	int Offset;
	int TotalRecords;
	int TotalPages;
	String Message;
	Resource Resource;
	
	public int getLimit() {
		return Limit;
	}
	public void setLimit(int limit) {
		Limit = limit;
	}
	public int getOffset() {
		return Offset;
	}
	public void setOffset(int offset) {
		Offset = offset;
	}
	public int getTotalRecords() {
		return TotalRecords;
	}
	public void setTotalRecords(int totalRecords) {
		TotalRecords = totalRecords;
	}
	public int getTotalPages() {
		return TotalPages;
	}
	public void setTotalPages(int totalPages) {
		TotalPages = totalPages;
	}
	public String getMessage() {
		return Message;
	}
	public void setMessage(String message) {
		Message = message;
	}
	public Resource getResource() {
		return Resource;
	}
	public void setResource(Resource resource) {
		Resource = resource;
	}
	
}
