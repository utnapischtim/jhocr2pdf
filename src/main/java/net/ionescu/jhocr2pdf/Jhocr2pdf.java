/**
 * Copyright (C) 2012 Ovidiu-Laurian Ionescu
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 */

package net.ionescu.jhocr2pdf;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Map;
import static java.util.logging.Level.FINE;
import static java.util.logging.Level.FINER;
import java.util.logging.Logger;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLReaderFactory;

import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Element;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.PdfContentByte;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.PdfStamper;

/**
 * Combines a PDF file containing scanned TIFF images with the OCR generated by Tesseract
 * @author Ovidiu Ionescu
 *
 */
public class Jhocr2pdf implements ContentHandler, ErrorHandler {
	private static Logger log = Logger.getLogger(Jhocr2pdf.class.getName());

	PdfStamper stamper;
	PdfContentByte canvas;
	BaseFont bf;
	float xScaling;
	float yScaling;
	
	boolean visible;
	int siglePageOCRNum = -1;
	
	
	StringBuilder text = new StringBuilder();
	String coords;
	@Override
	public void setDocumentLocator(Locator locator) {
		
	}

	@Override
	public void startDocument() throws SAXException {
		
	}

	@Override
	public void endDocument() throws SAXException {
		
	}

	@Override
	public void startPrefixMapping(String prefix, String uri)
			throws SAXException {
		
	}

	@Override
	public void endPrefixMapping(String prefix) throws SAXException {
		
	}

	@Override
	public void startElement(String uri, String localName, String qName,
			Attributes atts) throws SAXException {
		text.setLength(0);
		coords = null;
		
		if(localName.equals("span")) {
			log.finer("start of span, we encountered an OCR word");
			String klass = atts.getValue("class");
			if(null != klass && klass.equals("ocr_word")) {
				coords = atts.getValue("title");
			}
		}
		
		if(localName.equals("div")) {
			String klass = atts.getValue("class");
			if(null != klass && klass.equals("ocr_page")) {
				log.finer("start div, start of page, load the corresponding page of the PDF file");
				int pageNum = siglePageOCRNum;
				if(pageNum < 0) {
					String id = atts.getValue("id");
					pageNum = Integer.valueOf(id.split("_")[1], 10);
					log.log(FINE, "extracted page number from the div id attribute: {0}", pageNum);
				}
				if(visible) {
					canvas = stamper.getOverContent(pageNum);
				} else {
					canvas = stamper.getUnderContent(pageNum);
				}
				
				/*
				 * Calculate the scaling of the image.
				 * Tesseract coordinates differ from the PDF coordinates so we can't use them directly.
				 * But Tesseract gives us the dimension of the scanned image and we also know that the 
				 * image is as large as the page. So we calculate a scaling factor to translate from 
				 * Tesseract coordinates into PDF coordinates.
				 */
				
				String title[] = atts.getValue("title").split(" ");
				float imgWidth = Float.valueOf(title[title.length - 2]) - Float.valueOf(title[title.length - 4]);
				float imgHeight = Float.valueOf(title[title.length - 1]) - Float.valueOf(title[title.length - 3]);
				xScaling = PageSize.A4.getWidth() / imgWidth;
				yScaling = PageSize.A4.getHeight() / imgHeight;
				log.log(FINE, "Load page {0} with scaling {1}x{2}", new Object[] {pageNum, xScaling, yScaling});
			}
		}
	}

	@Override
	public void endElement(String uri, String localName, String qName)
			throws SAXException {
		// if this is closing a span containing an OCRed word then add the text to the PDF
		if(coords != null && text.length() > 0) {
			log.log(FINER, "OCRed word span closed, coords: {0} {1}", new Object[] {coords, text});
			addSpan(coords, text.toString());
		}
		coords = null;
	}

	@Override
	public void characters(char[] ch, int start, int length)
			throws SAXException {
		text.append(ch, start, length);
	}

	@Override
	public void ignorableWhitespace(char[] ch, int start, int length)
			throws SAXException {
		
	}

	@Override
	public void processingInstruction(String target, String data)
			throws SAXException {
		
	}

	@Override
	public void skippedEntity(String name) throws SAXException {
		
	}

	@Override
	public void warning(SAXParseException exception) throws SAXException {
		
	}

	@Override
	public void error(SAXParseException exception) throws SAXException {
		
	}

	@Override
	public void fatalError(SAXParseException exception) throws SAXException {
		
	}
	
	public void addSpan(String coords, String text) {
		// parse the coordinates
		String[] coord = coords.split(" ");
		Rectangle rect = new Rectangle(
				Float.valueOf(coord[1]), 
				Float.valueOf(coord[4]),
				Float.valueOf(coord[3]),
				Float.valueOf(coord[2]));
		
		canvas.saveState();
		canvas.beginText();
		canvas.setTextRenderingMode(PdfContentByte.TEXT_RENDER_MODE_FILL);
		canvas.setRGBColorFill(0xFF, 0, 0);
		canvas.setLineWidth(0);
		
		// calculate the font size
		float width = rect.getWidth() * xScaling;
		
		float tenWidth = bf.getWidthPointKerned(text, 10);
                float fontSize = 10 * width / tenWidth;
		
		canvas.setFontAndSize(bf, fontSize);
		canvas.showTextAlignedKerned(
				Element.ALIGN_LEFT, 
				text, 
				rect.getLeft() * xScaling, 
				PageSize.A4.getHeight() - rect.getBottom() * yScaling - bf.getDescentPoint(text, fontSize), 
				0);
		canvas.endText();
		canvas.restoreState();
		
	}
	
	
	@SuppressWarnings("unused")
	private  Jhocr2pdf() {
		
	}
	
	public Jhocr2pdf(PdfStamper stamper, boolean visible, String fontPath) throws DocumentException, IOException {
		this.stamper = stamper;
		this.visible = visible;
		if(fontPath != null) {
			bf = BaseFont.createFont(fontPath, BaseFont.WINANSI, BaseFont.EMBEDDED);
		} else {
			bf = BaseFont.createFont();
		}
	}
	
	/**
	 * @param pageNum page number if the OCR file corresponds to just one page, -1 for multi page
	 */
	public void parse(String fileName, int pageNum) throws SAXException, IOException {
		siglePageOCRNum = pageNum;
		XMLReader reader;
		reader = XMLReaderFactory.createXMLReader();
		reader.setContentHandler(this);
		reader.setErrorHandler(this);
		
		final BufferedReader characterStream = new BufferedReader(new InputStreamReader(new FileInputStream(fileName), "UTF8"));
		// skip past the DOCTYPE line
		characterStream.readLine();
		
		Reader filter = new Reader() {

			@Override
			public int read(char[] cbuf, int off, int len) throws IOException {
				int chars = characterStream.read(cbuf, off, len);
				//  filter out all non XML characters
				for(int i = 0; i < chars; i++) {
					int c = cbuf[i + off];
					if(c == 0x0009 || c == 0x000A || c == 0x000D
							|| (c >= 0x0020 && c <= 0xD7FF) 
							|| (0x10000 <= c && c <= 0x10FFFF)) {
								// c is XML valid, leave it alone
							} else {
								cbuf[i + off] = ' ';									
							}
				}	
				return chars;
			}

			@Override
			public void close() throws IOException {
				characterStream.close();
				
			}
			
		};
		reader.parse(new InputSource(filter));
	}
	
	/**
	 * @param args
	 * The arguments are:
	 *  [options] output_pdf
	 * The name of the output PDF file with the added OCR information must be the last argument
	 * Other 
	 * flags:
	 *   -i pdf_file PDF file containing the TIFF images
	 *   -hocr html_file hOCR file generated by Tesseract 
	 *   -visible render the text above the image (mostry for debugging)
	 *   -font font_path  path to the font file to use
	 *   -hocrnameformat string to use to construct the name of the hocr file depending on page number
	 *
	 * Usage example:
	 * java -jar jhorc2pdf.jar -font /usr/share/fonts/truetype/ttf-dejavu/DejaVuSans.ttf -i input.pdf -hocr input.html output.pdf
	 *
	 * @throws SAXException
	 * @throws IOException
	 * @throws DocumentException
	 */
	public static void main(String[] args) throws SAXException, IOException, DocumentException {
		/*
		 * process input parameters
		 * 
		 * -visible -font font_path inputPDF inputOCR outputPDF
		 */
		boolean visible = false;
		String fontPath = null;
		String fileNameFormat = null;
		String inputPDF = null;
		String inputHOCR = null;
		String outputPDF = null;
		
		for(int i = 0; i < args.length; i++) {
			if(args[i].startsWith("-")) {
				switch(args[i]) {

				case "-visible":
					visible = true;
					break;

				case "-font":
					fontPath = args[++i];
					break;

				case "-hocrnameformat":
					fileNameFormat = args[++i];
					break;

				case "-i":
					inputPDF = args[++i];
					break;

				case "-hocr":
					inputHOCR = args[++i];
					break;

				default:
					System.err.println("Invalid parameter: " + args[i]);
					System.exit(1);
				}
			} else {
				if(i < args.length - 1) {
					System.err.println("Ouput file name should be the last argument");
					System.exit(1);
				}
				outputPDF = args[i];
			}
		}
		
		log.fine("load the PDF file, initialize the Stamper");
		PdfReader reader = new PdfReader(inputPDF);
		PdfStamper stamper = new PdfStamper(reader, new FileOutputStream(outputPDF));
		
		Map<String, String> info = reader.getInfo();
		info.put("Title", new File(outputPDF).getName());
		stamper.setMoreInfo(info);

		Jhocr2pdf ocrStamp = new Jhocr2pdf(stamper, visible, fontPath);
		if(null != fileNameFormat) {
			log.fine("iterate through all the pages looking for separate hocr files");
			for(int i = 1, maxPages = reader.getNumberOfPages() + 1; i < maxPages ; i++) { 
				String hocrFileName = String.format(fileNameFormat, i);
				try {
					ocrStamp.parse(hocrFileName, i);
				} catch (SAXException saxException) {
					System.err.println("Error processing hocr file: " + hocrFileName);
					throw saxException;
				}
			}
		} else {
			ocrStamp.parse(inputHOCR, -1);
		}
		stamper.close();
	}
}
