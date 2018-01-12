package net.medhand.emc;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.Date;
import java.util.Map;
import java.util.TreeMap;

import com.google.gson.Gson;
import net.medhand.emc.model.Metadata;
import net.medhand.emc.model.Resource;
import net.medhand.emc.model.SPC;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;

public class EmcDownloadTool {
	static String baseURL = "http://api.uat05.medicines.org.uk/v1/";
	static String APIKEY = "17B9D964FDAA8145AC86A3AAD825F2CD";
	static String bookSrcPath = null;
	static String xmlDst = null;
	static String imgDst = null;
	static String outputXmlFile = null;
	static String fullRequestURL = null;
	static int offset = 3301;
	private static final Logger LOGGER = Logger.getLogger(EmcDownloadTool.class.getName());	
	static FileHandler fh;  
	static int newFilesCount = 0;
	static int newImagesCount = 0;
	static int deleteFilesCount = 0;
	static int deleteImagesCount = 0;
	static boolean fullMode = false;

	static TreeMap<String, String> titleAndId = new TreeMap<String , String>();
	static TreeMap<String, String> companyAndId = new TreeMap<String , String>();
	static TreeMap<String,String> companyList = new TreeMap<String, String>(); 
	static TreeMap<String,String> companyAndMedicinesList = new TreeMap<String, String>();

	public static void main(String[] args) {
		try {
			DateFormat df = new SimpleDateFormat("ddMMyyyy_HHmmss");
			String reportDate = df.format(new Date());
			
			if(!new File("log").isDirectory()) {
				new File("log").mkdir();
			}
			
			fh = new FileHandler("log/"+reportDate+".log"); 
			LOGGER.addHandler(fh);
	        SimpleFormatter formatter = new SimpleFormatter();  
	        fh.setFormatter(formatter);  
			
			LOGGER.info("Started: "+new Date());

			if(args.length==0) {
				System.out.println("Run with Book location Parameter. \n e.g: EmcDownloadTool c:/medhand/emc/");
				System.exit(0);
			}
			bookSrcPath = args[0];
			
			if(!(bookSrcPath.endsWith("/") || bookSrcPath.endsWith("\\"))) {
				bookSrcPath = bookSrcPath + "/";
			}
			xmlDst = bookSrcPath + "xml/xml/";
			imgDst = bookSrcPath + "xml/images/";
			outputXmlFile = bookSrcPath + "xml/demc.xml";
			String pullDate = null;
			String date = new SimpleDateFormat("YYYY-MM-DD").format(new Date());
			
			if(new File(outputXmlFile).isFile()) {
				FileInputStream stream = new FileInputStream(outputXmlFile);
				DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
				DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
				Document doc = dBuilder.parse(stream);
				Node rootNode = doc.getDocumentElement();
				pullDate = rootNode.getAttributes().getNamedItem("pullDate").getNodeValue();
			}
			
			//Read Map Data from File
			companyList = readFileToMap(bookSrcPath + "xml/companies.txt");
			companyAndMedicinesList = readFileToMap(bookSrcPath + "xml/companies&medicines.txt");
			
			/*1. Call API to read metadata & export*/
			LOGGER.info("\n\n\t\t### 1. Download XML");
			if(pullDate == null) {
				fullRequestURL = baseURL + "documents?state=authorised&offset=";
				LOGGER.info("Initializing Full download since last pull date is not available");
				fullMode = true;
				getMetatData();
			} else {
				LOGGER.info("Pulling only updates since the last pull date is :"+pullDate);
				
				LocalDate today = LocalDate.now();
				LocalDate lastPull = LocalDate.parse(pullDate);
				int diffDays = lastPull.until(today).getDays();
				//Get Retired
				fullRequestURL = baseURL + "documents?state=retired&daysoffset="+ diffDays +"&lastmodifieddateto=" + date + "&offset=";
				getMetatData();
				//Get New & Updated
				fullRequestURL = baseURL + "documents?state=authorised&daysoffset="+ diffDays +"&lastmodifieddateto=" + date + "&offset=";
				getMetatData();
				
			}
			// Update Companies to File
			writeMapToFile(bookSrcPath + "xml/companies.txt",companyList);
			writeMapToFile(bookSrcPath + "xml/companies&medicines.txt",companyAndMedicinesList);
			
			/*2. Download Images from the xml files downloaded above*/
			LOGGER.info("\n\n\t\t### 2. Download Images");
			downloadImages(new File(xmlDst));
			
			/*3. Create Manifest*/
			LOGGER.info("\n\n\t\t### 3. Generate Manifest");
			generateManifest(new File(xmlDst));
			
			
			LOGGER.info("\n New Files Downloaded: " + newFilesCount);
			LOGGER.info("\n New Images Downloaded: " + newImagesCount);
			LOGGER.info("\n Old Files Deleted: " + deleteFilesCount);
			LOGGER.info("\n Old Images Deleted: " + deleteImagesCount);
			LOGGER.info("\n\n****Completed: "+new Date());
		}
		catch(Exception e) {
			e.printStackTrace();
		}
		
	}
	
	public static void getMetatData() {
		try {
			if(!new File(xmlDst).isDirectory()) {
				new File(xmlDst).mkdir();
			}
			URL url = new URL(fullRequestURL+offset);
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod("GET");
			conn.setRequestProperty("Accept", "application/json");
			conn.setRequestProperty("ApiKey", APIKEY);

			if (conn.getResponseCode() != 200) {
				LOGGER.warning(fullRequestURL + offset + " not reachable. -"+conn.getResponseCode());
				throw new RuntimeException("Failed : HTTP error code : "
						+ conn.getResponseCode());
			}

			BufferedReader br = new BufferedReader(new InputStreamReader(
				(conn.getInputStream())));

			String output="";
			String responseJson="";

			while ((output = br.readLine()) != null) {
				responseJson = responseJson +"\n" + output;
			}
			
			Gson gson = new Gson();
			Metadata metadata = gson.fromJson(responseJson, Metadata.class);
			
			if(metadata.getTotalRecords()>0) {
				for(Resource resource:metadata.getResource()) {
					if(resource.getState().equalsIgnoreCase("Authorised")){
						getPage(resource.getId()+"");
						companyList.put(resource.getCompany().getId()+"", resource.getCompany().getName());
						companyAndMedicinesList.put(resource.getId()+"", resource.getCompany().getId()+"");
					} else if(resource.getState().equalsIgnoreCase("Retired")) {
						deletePage(resource.getId()+"");
					}
				}
				
				offset = offset + metadata.getLimit();
				
				conn.disconnect();
				br.close();
				
				if(metadata.getTotalRecords()>offset) {
					getMetatData();
				} else {
					LOGGER.info("Limit: "+ metadata.getLimit());
					LOGGER.info("Total Pages: " + metadata.getTotalPages());
					LOGGER.info("Total Records: " + metadata.getTotalRecords());
					LOGGER.info("OffSet: " + metadata.getOffset());
					LOGGER.info("Total Resources: " + metadata.getResource().size());
				}
			}
		}
		catch (Exception e) {
			LOGGER.warning(fullRequestURL + offset +" Something went wrong - "+e.getMessage());
			e.printStackTrace();
		}
		
	}
	
	public static void downloadImages(File path) {
		if(!new File(imgDst).isDirectory()) {
			new File(imgDst).mkdir();
		}
		
		for(File file:path.listFiles()) {
			try {
				DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
				dbFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
				DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
				
				FileInputStream stream = new FileInputStream(file);
				Document doc = dBuilder.parse(stream);
				NodeList nodes = doc.getElementsByTagName("IMG");
				int size = nodes.getLength();
				for(int i=0;i<size;i++) {
					Element ele = (Element) nodes.item(i);
					getImage(ele.getAttribute("SRC"),file.getName());
				}
				
			} catch (Exception e) {
				LOGGER.warning(" Something went wrong in the file - " + file.getAbsolutePath() + " \n "+e.getMessage());
			}
		}
	}
	
	public static void getImage(String imgSrc, String fileName) {
		try {
			//./SPC.2175.12_FILES/IMAGE001.GIF ==> spc~2175~12~IMAGE001.GIF
			//1 Remove ./ 
			String imgURL= imgSrc.replace("./","");
		
			//2 Get image file & folder names
			String imageFolder = imgURL.split("/")[0];
			String imageName = imgURL.split("/")[1];
			
			String imgPath = imgDst+ imageFolder + "_" +imageName;
			
			//3 Construct http url
			imgURL = imageFolder.split("_FILES")[0].toLowerCase().replace(".", "~")+"~"+imageName;
			
			if(!new File(imgPath).exists()) {
				URL url = new URL(baseURL+"images/"+imgURL);
				
				HttpURLConnection conn = (HttpURLConnection) url.openConnection();
				conn.setRequestMethod("GET");
				conn.setRequestProperty("Accept", "image/gif");
				conn.setRequestProperty("ApiKey", APIKEY);
				
				InputStream in = conn.getInputStream();
			    FileOutputStream out = new FileOutputStream(imgPath);
			    
			    int c;
		        byte[] b = new byte[1024];
		        while ((c = in.read(b)) != -1) {
		            out.write(b, 0, c);
		        }
		        newImagesCount++;
		        in.close();
		        out.flush();
		        out.close();
				conn.disconnect();
			}
		}
		catch (Exception e) {
			LOGGER.warning(" Something went wrong - "+e.getMessage() + "\n File: "+fileName);
			e.printStackTrace();

		}
	}
	
	public static void getPage(String ID) {
		try {
			if(!new File(xmlDst+ID+".xml").isFile() || !fullMode) {
				String requestURL = baseURL + "spcs/"+ID+"?format=xml";
				URL url = new URL(requestURL);
				HttpURLConnection conn = (HttpURLConnection) url.openConnection();
				conn.setRequestMethod("GET");
				conn.setRequestProperty("Accept", "application/json");
				conn.setRequestProperty("ApiKey", APIKEY);
	
				if (conn.getResponseCode() != 200) {
					throw new RuntimeException("Failed : HTTP error code : "
							+ conn.getResponseCode());
				}
	
				BufferedReader br = new BufferedReader(new InputStreamReader(
					(conn.getInputStream())));
				BufferedWriter writer = new BufferedWriter(new FileWriter(new File(xmlDst+ID+".xml")));
				
				String output="";
				String responseJson="";
	
				while ((output = br.readLine()) != null) {
					responseJson = responseJson + output;
				}
				
				Gson gson = new Gson();
				SPC SPCData = gson.fromJson(responseJson, SPC.class);
	
				writer.write(SPCData.getResource().getContent());
				newFilesCount++;
	
				br.close();
				writer.flush();
				writer.close();
				conn.disconnect();
			}	
		}
		catch (Exception e) {
			LOGGER.warning(" Something went wrong - "+e.getMessage());
			e.printStackTrace();

		}
	}
	
	public static void deletePage(String ID) {
		File tarXml = new File(xmlDst+ID+".xml");
		if(tarXml.isFile()){
			try {
				DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
				dbFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
				DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
				
				FileInputStream stream = new FileInputStream(tarXml);
				Document doc = dBuilder.parse(stream);
				NodeList nodes = doc.getElementsByTagName("IMG");
				int size = nodes.getLength();
				for(int i=0;i<size;i++) {
					Element ele = (Element) nodes.item(i);
					String imagePath = imgDst+ele.getAttribute("SRC"); 
					imagePath = imagePath.replace("./","");
					imagePath = imagePath.replace("/","_");
					File img = new File(imagePath);
					if(img.isFile()) {
						img.delete();
						deleteImagesCount++;
					}
				}	
				tarXml.delete();
				deleteFilesCount++;
			} catch (Exception e) {
				LOGGER.warning(" Something went wrong in the file - " + tarXml.getAbsolutePath() + " \n "+e.getMessage());
			}
		}
	}
	
	public static void generateManifest(File path) {
		for(File file:path.listFiles()) {
			readXML(file);
		}
		
		buildManifest();
	}
	
	public static void buildManifest() {
		DocumentBuilderFactory dbfac = DocumentBuilderFactory.newInstance();
        DocumentBuilder docBuilder;	
        Document doc=null;
        try {
			docBuilder = dbfac.newDocumentBuilder();
	        doc = docBuilder.newDocument();
	        Element root = doc.createElement("manifest");
	        root.setAttribute("title", "eMC Medicines Information Services");
	        root.setAttribute("pullDate", new SimpleDateFormat("YYYY-MM-DD").format(new Date()));
	        doc.appendChild(root);
	        
	        Element medicines = doc.createElement("medicines");
	        medicines.setAttribute("title", "MEDICINES");
	        Element companies = doc.createElement("companies");
	        companies.setAttribute("title", "COMPANIES");
	        root.appendChild(medicines);
	        root.appendChild(companies);
	        
	        String partVar = "";
	        Element alphaPart = null;
	        //MEDICINES
	        for (Map.Entry<String, String> entry : titleAndId.entrySet()) {
		        Element manifestFile = null;
				String title = entry.getKey().split(";")[0];
				String file = entry.getValue();
				
				String titleChar = title.substring(0, 1);
				char ch = titleChar.charAt(0);
				if(!(ch>='a' && ch<='z') && !(ch>='A' && ch<='Z')) {
					titleChar = "0-9";
				}
				
				if(!partVar.equalsIgnoreCase(titleChar)) {
					if(alphaPart!=null) {
						medicines.appendChild(alphaPart);
					}
					partVar = titleChar;
					alphaPart = doc.createElement("part");
					alphaPart.setAttribute("title", partVar.toUpperCase());
				}	
				
				manifestFile=doc.createElement("manifest-file");
				manifestFile.setAttribute("id", file.split(".xml")[0]);
				manifestFile.setAttribute("src", "xml\\"+file);
				manifestFile.setAttribute("title", title.trim());				
				alphaPart.appendChild(manifestFile);
	        }
	        if(alphaPart!=null) { //last set
				medicines.appendChild(alphaPart);
			}
	        alphaPart = null;
	        partVar = "";
	        Element company= null;
	        //COMPANIES
	        String lastCompanyName = "";
	        TreeMap<String, String> drugList = new TreeMap<String , String>();
	        for (Map.Entry<String, String> entry : companyAndId.entrySet()) {
	        	try {
					String title = entry.getKey().split(";")[0];
					String file = entry.getKey().split(";")[1];
					String drugTitle = entry.getValue();
					
					String titleChar = title.substring(0, 1);
					char ch = titleChar.charAt(0);
					if(!(ch>='a' && ch<='z') && !(ch>='A' && ch<='Z')) {
						titleChar = "0-9";
					}
						
					if(!lastCompanyName.equalsIgnoreCase(title)) {
						if(company!=null){
							for (Map.Entry<String, String> drugEntry : drugList.entrySet()) {
								Element drugName = doc.createElement("drug");
								drugName.setAttribute("title", drugEntry.getKey());
								drugName.setAttribute("id", drugEntry.getValue());
								company.appendChild(drugName);
								drugList = new TreeMap<String , String>();
							}
							alphaPart.appendChild(company);	
						}
						lastCompanyName = title;
						company=doc.createElement("company");
						company.setAttribute("title", title.trim());				
					}
					drugList.put(drugTitle.trim(), file.split(".xml")[0]);
					
					if(!partVar.equalsIgnoreCase(titleChar)) {
						if(alphaPart!=null) {
							companies.appendChild(alphaPart);
						}
						partVar = titleChar;
						alphaPart = doc.createElement("part");
						alphaPart.setAttribute("title", partVar.toUpperCase());
					}
	        	} catch (Exception e) {
	        		LOGGER.warning(" Something went wrong - "+e.getMessage());
	        	}
	        }
	        if(company!=null){
	        	for (Map.Entry<String, String> drugEntry : drugList.entrySet()) {
					Element drugName = doc.createElement("drug");
					drugName.setAttribute("title", drugEntry.getKey());
					drugName.setAttribute("id", drugEntry.getValue());
					company.appendChild(drugName);
				}
				alphaPart.appendChild(company);	
			}
	        if(alphaPart!=null) { //last set
	        	companies.appendChild(alphaPart);
			}
	        
	        buildManifestText(doc);
        } catch (Exception e) {
			LOGGER.warning(" Something went wrong - "+e.getMessage());
		}
	}
	
	public static void buildManifestText(Document doc){
		try{
	        TransformerFactory transfac = TransformerFactory.newInstance();
            Transformer trans = transfac.newTransformer();
            trans.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            trans.setOutputProperty(OutputKeys.INDENT, "yes");
            trans.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            //create string from xml tree
            StringWriter sw = new StringWriter();
            StreamResult result = new StreamResult(sw);
            DOMSource source = new DOMSource(doc);
            trans.transform(source, result);
            String xmlString = sw.toString();
            //print xml
            writeFile(xmlString);

		} catch(Exception e){
			LOGGER.severe(e.toString());
		}
		
	}
	
	public static void readXML(File fXmlFile){
		
		FileInputStream stream = null;
		try {
			
			DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
			dbFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
			DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
			
			stream = new FileInputStream(fXmlFile);
			Document doc = dBuilder.parse(stream);
			String title = doc.getElementsByTagName("TITLE").item(0).getTextContent();
			if(title != null && title.trim().length()!=0) {
				title = title.trim();
				title = title.substring(0, 1).toUpperCase() + title.substring(1);
				titleAndId.put(title + ";" + fXmlFile.getName(), fXmlFile.getName());
			}
			
			String companyTitle = companyList.get(companyAndMedicinesList.get(fXmlFile.getName().split(".xml")[0]));
			companyTitle = companyTitle.substring(0,1).toUpperCase() + companyTitle.substring(1); 
			companyAndId.put(companyTitle + ";"+fXmlFile.getName(), doc.getElementsByTagName("TITLE").item(0).getTextContent().trim());
			
			
		} catch (Exception e) {
			LOGGER.warning(" Something went wrong in the file - " + fXmlFile.getAbsolutePath() + " \n "+e.getMessage());
			e.printStackTrace();
		}
	}
	
	public static void writeFile(String file){
		try {
			BufferedWriter out = new BufferedWriter(new FileWriter(outputXmlFile));
			out.write(file);
			out.close();
			} catch (Exception e) { 
				e.printStackTrace(); 
				LOGGER.severe(e.toString());
				System.exit(-1);
				}	
	}
	
	public static void writeMapToFile(String file, TreeMap<String, String> mapData) {
		try {
			BufferedWriter out = new BufferedWriter(new FileWriter(file));
			for(Map.Entry<String,String> m :mapData.entrySet()){
				out.write(m.getKey()+"="+m.getValue()+"\n");
			}
			out.close();
			} 
		catch (Exception e) { 
			e.printStackTrace(); 
			LOGGER.severe(e.toString());
		}
	}
	
	public static TreeMap<String,String> readFileToMap(String file) {
		TreeMap<String, String> mapData = new TreeMap<String, String>();
		try {
			if(new File(file).isFile()){
				BufferedReader br = new BufferedReader(new FileReader(file));
				String output="";
				while ((output = br.readLine()) != null) {
					mapData.put(output.split("=")[0], output.split("=")[1]);
				}
				br.close();
			}
		} 
		catch (Exception e) { 
			e.printStackTrace(); 
			LOGGER.severe(e.toString());
		}
		return mapData;
	}
}
