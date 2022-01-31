package com.brailsoft.managment.report;

import java.io.File;
import java.io.FileNotFoundException;
import java.time.format.DateTimeFormatter;
import java.util.logging.Logger;

import com.brailsoft.property.management.constant.Constants;
import com.brailsoft.property.management.constant.DateFormats;
import com.brailsoft.property.management.launcher.PropertyManager;
import com.brailsoft.property.management.logging.PropertyManagerLogConfigurer;
import com.brailsoft.property.management.model.MonitoredItem;
import com.brailsoft.property.management.model.PropertyMonitor;
import com.brailsoft.property.management.persistence.LocalStorage;
import com.brailsoft.property.management.preference.ApplicationPreferences;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.AreaBreak;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.UnitValue;

public class ReportApplication {
	private static final String CLASS_NAME = ReportApplication.class.getName();
	private static final Logger LOGGER = Logger.getLogger(Constants.LOGGER_NAME);

	private Object waitForLoad = new Object();
	private boolean loadCompleted = false;
	private File rootDirectory = null;
	private LocalStorage localStorage = null;
	private PropertyMonitor mon = null;
	private File pdfFile = null;
	private PdfWriter writer = null;
	private PdfDocument pdf = null;
	private Document document = null;
	private Table table = null;
	private PdfFont font = null;
	private PdfFont bold = null;
	private DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern(DateFormats.dateFormatForUI);

	public static void main(String[] args) {
		LOGGER.entering(CLASS_NAME, "start", args);
		configurePreferences(args);
		runApplication();
		shutDown();
		LOGGER.exiting(CLASS_NAME, "start");
	}

	private static void runApplication() {
		LOGGER.entering(CLASS_NAME, "runApplication");
		ReportApplication application = new ReportApplication();
		application.generateReport();
		LOGGER.exiting(CLASS_NAME, "runApplication");
	}

	private static void shutDown() {
		LOGGER.entering(CLASS_NAME, "shutDown");
		PropertyManagerLogConfigurer.shutdown();
		PropertyManager.executor().shutdown();
		LOGGER.exiting(CLASS_NAME, "shutDown");
	}

	private static void configurePreferences(String[] args) {
		String preferencesName = Constants.NODE_NAME;
		if (args.length != 0) {
			preferencesName = args[0];
		}
		ApplicationPreferences.getInstance(preferencesName);
		PropertyManagerLogConfigurer.setUp();
	}

	private ReportApplication() {
	}

	private void generateReport() {
		LOGGER.entering(CLASS_NAME, "generateReport");
		writeToConsole("is starting");
		configureLocalStorage();
		try {
			createFonts();
			loadData();
			createPdfFile();
			createPdfDocument();
			writePdfReport();
			closePdfDocument();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			writeToConsole("has ended");
			LOGGER.exiting(CLASS_NAME, "generateReport");
		}
	}

	private void createFonts() {
		LOGGER.entering(CLASS_NAME, "createFonts");
		try {
			font = PdfFontFactory.createFont(StandardFonts.HELVETICA);
			bold = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
		} catch (Exception e) {
			LOGGER.warning("Caught exception: " + e.getMessage());
			e.printStackTrace();
		} finally {
			LOGGER.exiting(CLASS_NAME, "createFonts");
		}
	}

	private void closePdfDocument() {
		LOGGER.entering(CLASS_NAME, "closePdfDocument");
		writeToConsole("is closing the document");
		if (document != null) {
			writeToConsole("has written the report to " + pdfFile.getAbsolutePath());
			document.close();
		}
		LOGGER.exiting(CLASS_NAME, "closePdfDocument");
	}

	private void writePdfReport() {
		LOGGER.entering(CLASS_NAME, "writePdfReport");
		writeToConsole("is writing to the document");
		boolean notFirst = false;
		for (com.brailsoft.property.management.model.Property property : mon.getProperties()) {
			if (notFirst) {
				document.add(new AreaBreak());
			}
			writeToConsole("is processing " + property);
			document.add(new Paragraph(property.toString()).setFontSize(18).setUnderline());
			table = buildTable();
			for (MonitoredItem item : property.getItems()) {
				addItemToTable(item);
			}
			document.add(table);
			notFirst = true;
		}
		LOGGER.exiting(CLASS_NAME, "writePdfReport");
	}

	private void addItemToTable(MonitoredItem item) {
		LOGGER.entering(CLASS_NAME, "addItemToTable", item);
		table.addCell(new Cell().add(new Paragraph(item.getDescription()).setFont(font)));
		table.addCell(new Cell().add(new Paragraph(item.getLastActionPerformed().format(dateFormatter)).setFont(font)));
		table.addCell(new Cell().add(new Paragraph(item.getTimeForNextNotice().format(dateFormatter)).setFont(font)));
		if (item.overdue()) {
			table.addCell(new Cell().setFontColor(ColorConstants.RED)
					.add(new Paragraph(item.getTimeForNextAction().format(dateFormatter)).setFont(font)));
		} else if (item.noticeDue()) {
			table.addCell(new Cell().setFontColor(ColorConstants.ORANGE)
					.add(new Paragraph(item.getTimeForNextAction().format(dateFormatter)).setFont(font)));
		} else {
			table.addCell(
					new Cell().add(new Paragraph(item.getTimeForNextAction().format(dateFormatter)).setFont(font)));
		}
		LOGGER.exiting(CLASS_NAME, "addItemToTable");
	}

	private Table buildTable() {
		LOGGER.entering(CLASS_NAME, "buildTable");
		Table table = new Table(new float[] { 4, 1, 1, 1 });
		table.setWidth(UnitValue.createPercentValue(100));
		table.addHeaderCell(new Cell().add(new Paragraph("Descrition                      ").setFont(bold)));
		table.addHeaderCell(new Cell().add(new Paragraph("Last Action").setFont(bold)));
		table.addHeaderCell(new Cell().add(new Paragraph("Next Notification").setFont(bold)));
		table.addHeaderCell(new Cell().add(new Paragraph("Next Action").setFont(bold)));
		LOGGER.exiting(CLASS_NAME, "buildTable");
		return table;
	}

	private void createPdfDocument() {
		LOGGER.entering(CLASS_NAME, "createPdfDocument");
		writeToConsole("is creating the document");
		try {
			writer = new PdfWriter(pdfFile);
			pdf = new PdfDocument(writer);
			document = new Document(pdf);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		LOGGER.exiting(CLASS_NAME, "createPdfDocument");
	}

	private void createPdfFile() {
		LOGGER.entering(CLASS_NAME, "createPdfFile");
		writeToConsole("is creating the report file");
		mon = PropertyMonitor.getInstance();
		pdfFile = new File(ApplicationPreferences.getInstance().getReportDirectory(), "property.pdf");
		LOGGER.info(pdfFile.getAbsolutePath());
		writeToConsole("will write the report to " + pdfFile.getAbsolutePath());
		LOGGER.exiting(CLASS_NAME, "createPdfFile");
	}

	private void loadData() throws Exception {
		LOGGER.entering(CLASS_NAME, "loadData");
		writeToConsole("is loading data");
		localStorage.loadStoredData();
		writeToConsole("is waiting for load to complete (max 5 seconds)");
		synchronized (waitForLoad) {
			waitForLoad.wait(5000);
		}
		writeToConsole("Wait complete");
		LOGGER.info("loadCompleted: " + loadCompleted);
		if (!loadCompleted) {
			writeToConsole("failed to load data successfully");
			LOGGER.exiting(CLASS_NAME, "loadData");
			System.exit(0);
		}
		LOGGER.exiting(CLASS_NAME, "loadData");
	}

	private void configureLocalStorage() {
		LOGGER.entering(CLASS_NAME, "configureLocalStroage");
		rootDirectory = new File(ApplicationPreferences.getInstance().getDirectory());
		LOGGER.info(rootDirectory.getAbsolutePath());
		localStorage = LocalStorage.getInstance(rootDirectory);
		localStorage.addListener(l -> loadComplete());
		LOGGER.exiting(CLASS_NAME, "configureLocalStroage");
	}

	private void loadComplete() {
		LOGGER.entering(CLASS_NAME, "loadComplete");
		synchronized (waitForLoad) {
			loadCompleted = true;
			waitForLoad.notifyAll();
		}
		LOGGER.exiting(CLASS_NAME, "loadComplete");
	}

	private void writeToConsole(String message) {
		System.out.println("ReportApplication: " + message);
	}

}
