package opengdc.util;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import javax.swing.JTextPane;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import opengdc.GUI;
import opengdc.Settings;
import opengdc.parser.BioParser;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 *
 * @author fabio
 */
public class MetadataHandler {

    public static final String __OPENGDCSEP__ = "__opengdcsep__";

    // for createMap function only
    private static int keyPrefixCounterForUniqueness = 0;

    // cast to HashMap<String, Object>
    private static Object createMap(Node source, boolean restartKeyPrefixCounter) {
        if (restartKeyPrefixCounter) {
            keyPrefixCounterForUniqueness = 0;
        }
        HashMap<String, Object> tmpMap = new HashMap<>();
        try {
            if(!source.getNodeName().contains("additional_studies")){
                int childs = source.getChildNodes().getLength();
                if (childs == 0) {
                    if (source.getTextContent().trim().equals("")) {
                        return "NA";
                    }
                    return source.getTextContent();
                } else {
                    HashMap<String, Object> dataTmp = new HashMap<>();
                    for (int i = 0; i < childs; i++) {
                        Node child = source.getChildNodes().item(i);
                        Object child_data = createMap(child, false);
                        if (child.getNodeName().toLowerCase().trim().contains("#text") && childs <= 1) {
                            return ((String) child_data).replaceAll("\t", " ");
                        } else if (!child.getNodeName().toLowerCase().trim().contains("#text")) {
                            dataTmp.put(keyPrefixCounterForUniqueness + "_" + child.getNodeName(), child_data);
                            keyPrefixCounterForUniqueness++;
                        }
                    }
                    tmpMap.put(keyPrefixCounterForUniqueness + "_" + source.getNodeName(), dataTmp);
                    keyPrefixCounterForUniqueness++;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return tmpMap;
    }

    /*private static void printMap(Object map, int level) {
        String level_tab = "";
        for (int i=0; i<level; i++)
            level_tab += "\t";

        HashMap<String, Object> hash_map = (HashMap<String, Object>)map;
        for (String k: hash_map.keySet()) {
            if (hash_map.get(k) instanceof HashMap) {
                HashMap<String, Object> values = (HashMap<String, Object>)hash_map.get(k);
                System.err.println(level_tab + k);
                if (!values.isEmpty())
                    printMap(values, level+1);
            }
            else if (hash_map.get(k) instanceof String) {
                String value = (String)hash_map.get(k);
                System.err.println(level_tab + k + "\t" + value);
            }
        }
    }*/
    public static HashMap<String, Object> getXMLMap(String file_path) {
        HashMap<String, Object> result = new HashMap<>();
        try {
            DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
            Document doc = docBuilder.parse(new File(file_path));
            // normalize text representation
            doc.getDocumentElement().normalize();
            //System.out.println ("Root element of the doc is " + doc.getDocumentElement().getNodeName());
            NodeList roots = doc.getChildNodes();
            //System.err.println(roots.getLength());

            for (int i = 0; i < roots.getLength(); i++) {
                Node node = roots.item(i);
                result = (HashMap<String, Object>) createMap(node, true);
                break;
            }
        } catch (SAXParseException err) {
            System.out.println("** Parsing error" + ", line " + err.getLineNumber() + ", uri " + err.getSystemId());
            System.out.println(" " + err.getMessage());
        } catch (SAXException e) {
            Exception x = e.getException();
            ((x == null) ? e : x).printStackTrace();
        } catch (ParserConfigurationException | IOException | DOMException t) {
            t.printStackTrace();
        }

        return result;
    }

    public static HashMap<String, HashMap<String, String>> getTSVMap(String file_path, String indexBy) {
        HashMap<String, HashMap<String, String>> result = new HashMap<>();

        try {
            InputStream fstream = new FileInputStream(file_path);
            DataInputStream in = new DataInputStream(fstream);
            BufferedReader br = new BufferedReader(new InputStreamReader(in));
            int line_count = 0;
            int index_position = 0;
            String[] header = null;
            String line;
            while ((line = br.readLine()) != null) {
                if (!line.trim().equals("")) {
                    String[] line_split = line.split("\t");
                    if (line_count == 0) { // header
                        header = line_split;
                        for (int i = 0; i < header.length; i++) {
                            if (header[i].toLowerCase().trim().equals(indexBy)) {
                                index_position = i;
                                break;
                            }
                        }
                    } else { // content
                        String key = line_split[index_position];
                        HashMap<String, String> values = new HashMap<>();
                        for (int i = 0; i < line_split.length; i++) {
                            if (i != index_position) {
                                values.put(header[i], line_split[i].replaceAll("\t", " "));
                            }
                        }
                        result.put(key, values);
                    }
                    line_count++;
                }
            }
            br.close();
            in.close();
            fstream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return result;
    }

    // https://gist.github.com/madan712/3912272
    public static HashMap<String, HashMap<String, String>> getXLSXMap(JTextPane pane, String file_path, String indexBy) {
        HashMap<String, HashMap<String, String>> result = new HashMap<>();

        try {
            InputStream excelFileToRead = new FileInputStream(file_path);
            XSSFWorkbook wb = new XSSFWorkbook(excelFileToRead);

            int sheets = wb.getNumberOfSheets();
            for (int sheet_index = 0; sheet_index < sheets; sheet_index++) {
                XSSFSheet sheet = wb.getSheetAt(sheet_index);

                String sheet_name = sheet.getSheetName();
                if (!sheet_name.toLowerCase().trim().contains("criteria")
                        && !sheet_name.toLowerCase().trim().contains("original")) { // skip sheets
                    XSSFRow row;
                    XSSFCell cell;
                    Iterator rows = sheet.rowIterator();

                    //String criteria_str = "";
                    int header_rows = 1;
                    int current_row = 0;

                    ArrayList<String> header = new ArrayList<>();
                    int indexBy_position = 0;

                    while (rows.hasNext()) {
                        row = (XSSFRow) rows.next();

                        Iterator cells = row.cellIterator();
                        //String row_str = "";
                        HashMap<String, String> content_map = new HashMap<>();
                        String indexBy_value = "";

                        int cell_index = 0;
                        while (cells.hasNext()) {
                            cell = (XSSFCell) cells.next();

                            String cellValue = "";
                            if (cell.getCellTypeEnum() == CellType.STRING) {
                                cellValue = cell.getStringCellValue();
                            } else if (cell.getCellTypeEnum() == CellType.NUMERIC) {
                                cellValue = String.valueOf(cell.getNumericCellValue());
                            }
                            cellValue = cellValue.replaceAll("\r\n", " ").replaceAll("\n", " ").replaceAll("\t", " ");

                            if (!cellValue.trim().equals("")) {

                                //String cellValue = cell.getRawValue();
                                //System.err.println(cellValue);
                                // cell is in merged region?
                                int cellIterations = 1;
                                List<CellRangeAddress> mergedRegionsInSheet = sheet.getMergedRegions();
                                if (mergedRegionsInSheet.size() > 0) {
                                    header_rows = 2;
                                }
                                for (CellRangeAddress mergedRegion : mergedRegionsInSheet) {
                                    if (mergedRegion.isInRange(cell)) {
                                        cellIterations = mergedRegion.getNumberOfCells();
                                        //if (mergedRegion.containsRow(current_row-1))
                                        //cellIterations = cellIterations/2;
                                        cellIterations = cellIterations / (((mergedRegion.getLastRow() + 1) - (mergedRegion.getFirstRow() + 1)) + 1);
                                        break;
                                    }
                                }

                                if (current_row < header_rows) { //header
                                    for (int cell_iter = 0; cell_iter < cellIterations; cell_iter++) {
                                        String prefix_header = "";
                                        try {
                                            prefix_header = header.get(cell_index + cell_iter);
                                        } catch (Exception e) {
                                        /* first line - prefix_header does not yet exist */ };
                                        String suffix_header = cellValue;

                                        String header_str = "";
                                        if (prefix_header.trim().equals("") && !suffix_header.trim().equals("")) {
                                            header_str = suffix_header;
                                        } else if (!prefix_header.trim().equals("") && suffix_header.trim().equals("")) {
                                            header_str = prefix_header;
                                        } else if (prefix_header.trim().equals(suffix_header.trim())) {
                                            header_str = prefix_header;
                                        } else if (!prefix_header.trim().equals("") && !suffix_header.trim().equals("")) {
                                            header_str = prefix_header + __OPENGDCSEP__ + suffix_header;
                                        }

                                        //System.err.println(header_str + "\t" + cellIterations);
                                        if (header_str.toLowerCase().trim().equals(indexBy)) {
                                            indexBy_position = cell_index + cell_iter;
                                        }

                                        try {
                                            header.remove(cell_index + cell_iter);
                                            header.add(cell_index + cell_iter, header_str);
                                            /*if (current_row == 1)
                                                System.err.println(header_str);*/
                                        } catch (Exception e) {
                                            header.add(header_str);
                                        }

                                        //cell_index++;
                                        cell_index += cellIterations;
                                    }
                                } else { // content
                                    try {
                                        content_map.put(sheet_name + __OPENGDCSEP__ + header.get(cell_index), cellValue);
                                        if (cell_index == indexBy_position) {
                                            indexBy_value = cellValue;
                                        }
                                        cell_index++;
                                    } catch (Exception e) {
                                        wb.close();
                                        excelFileToRead.close();
                                        File xlsxFile = new File(file_path);
                                        System.err.println("ERROR [malformed input format]: An error has occurred while reading the XLSX file. Please control the structure of " + xlsxFile.getName());
                                        GUI.appendLog(pane, "\n ERROR [malformed input format]: An error has occurred while reading the XLSX file. Please control the structure of " + xlsxFile.getName());
                                        return new HashMap<>();
                                    }
                                }
                            } else {
                                cell_index++;
                            }
                        }

                        if (current_row >= header_rows) { // content
                            if (!indexBy_value.trim().equals("")) {
                                if (result.containsKey(indexBy_value)) {
                                    content_map.putAll(result.get(indexBy_value));
                                }
                                result.put(indexBy_value, content_map);
                            }
                        }
                        //criteria_str += row_str;
                        current_row++;
                        //System.out.println();
                    }

                    /*if (!criteria_str.trim().equals("")) {
                        HashMap<String, String> criteria_sheet = new HashMap<>();
                        criteria_sheet.put("criteria", criteria_str);
                        result.put(sheet_name, criteria_sheet);
                    }*/
                }
            }
            wb.close();
            excelFileToRead.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return result;
    }

    public static HashMap<String, String> extractAdminInfo(HashMap<String, Object> xml_map) {
        HashMap<String, String> admin_map = new HashMap<>();
        HashMap<String, String> dataMap = getDataMap(xml_map, null);
        for (String key : dataMap.keySet()) {
            if (key.contains("admin")) {
                admin_map.put(key, dataMap.get(key));
            }
        }
        return admin_map;
    }

    public static HashMap<String, String> getDataMap(HashMap<String, Object> map, HashMap<String, String> dataMap) {
        if (dataMap == null) {
            dataMap = new HashMap<>();
        }
        for (String k : map.keySet()) {
            if (map.get(k) instanceof HashMap) {
                HashMap<String, Object> values = (HashMap<String, Object>) map.get(k);
                if (!values.isEmpty()) {
                    getDataMap(values, dataMap);
                }
            } else if (map.get(k) instanceof String) {
                String value = (String) map.get(k);
                dataMap.put(k, value);
            }
        }
        return dataMap;
    }

    public static String findKey(HashMap<String, String> map, String searchCondition, String str) {
        String value = "";
        if (!map.isEmpty()) {
            for (String k : map.keySet()) {
                if (searchCondition.toLowerCase().trim().equals("endswith")) {
                    if (k.toLowerCase().trim().endsWith(str)) {
                        value = map.get(k);
                        break;
                    }
                } else if (searchCondition.toLowerCase().trim().equals("startswith")) {
                    if (getAttributeFromKey(k).toLowerCase().trim().startsWith(str)) {
                        value = map.get(k);
                        break;
                    }
                } else if (searchCondition.toLowerCase().trim().equals("contains")) {
                    if (k.toLowerCase().trim().contains(str)) {
                        value = map.get(k);
                        break;
                    }
                } else if (searchCondition.toLowerCase().trim().equals("equals")) {
                    if (getAttributeFromKey(k).toLowerCase().trim().equals(str)) {
                        value = map.get(k);
                        break;
                    }
                }
            }
        }
        return value;
    }

    private static ArrayList<XMLNode> aliquotNodes = new ArrayList<>();

    public static void searchForAliquots(XMLNode root) {
        if (root.getLabel().toLowerCase().trim().endsWith("bio:aliquot") && root.getAttributes().size() > 0) {
            aliquotNodes.add(root);
            //System.err.println(root.getLabel() + "\t" + "attributes: " + root.getAttributes().size());
        } else if (root.hasChilds()) {
            for (XMLNode child : root.getChilds()) {
                searchForAliquots(child);
            }
        }
    }

    public static ArrayList<XMLNode> getAliquotNodes() {
        return aliquotNodes;
    }

    public static void emptyAliquotNodes() {
        aliquotNodes = new ArrayList<>();
    }

    public static XMLNode convertMapToIndexedTree(HashMap<String, Object> map, XMLNode root) {
        for (String k : map.keySet()) {
            if (map.get(k) instanceof HashMap) {
                XMLNode child = new XMLNode();
                child.setLabel(k);
                child.setParent(root);
                root.addChild(convertMapToIndexedTree((HashMap<String, Object>) map.get(k), child));
            } else if (map.get(k) instanceof String) {
                String value = (String) map.get(k);
                root.addAttribute(k, value);
            }
        }
        return root;
    }

    public static HashMap<String, String> extractParentMetadata(XMLNode node, HashMap<String, String> meta) {
        if (meta == null) {
            meta = new HashMap<>();
        }
        meta.putAll(node.getAttributes());

        if (node.getParent() == null) {
            return meta;
        }
        return extractParentMetadata(node.getParent(), meta);
    }

    public static String getAttributeFromKey(String key) {
        String[] key_split = key.split("_");
        return key.substring(key_split[0].length() + 1, key.length());
    }

    // the attributes in this methods are all required 
    public static HashMap<String, HashMap<String, Object>> getAdditionalManuallyCuratedAttributes(String program, String disease, String dataType, String format, String aliquot_uuid, String aliquot_brc, HashMap<String, String> biospecimen_attributes, HashMap<String, String> clinical_attributes, HashMap<String, String> gdc_attributes, String suffix_id, String manually_curated_dataType) {
        String attributes_prefix = "manually_curated";
        String category_separator = "__";

        /**
         * ***** tissue_status ******
         */
        // retrieve 'manually_curated__tissue_status' from 'biospecimen__bio__sample_type_id'
        HashMap<String, HashMap<String, Object>> additional_attributes = new HashMap<>();
        String tissue_id = "";
        boolean sample_type_id_found = false;
        for (String bio_attr : biospecimen_attributes.keySet()) {
            if (bio_attr.trim().toLowerCase().contains("sample_type_id")) {
                tissue_id = biospecimen_attributes.get(bio_attr);
                sample_type_id_found = true;
                break;
            }
        }
        if (!sample_type_id_found) {
            if (!aliquot_brc.trim().equals("")) {
                tissue_id = aliquot_brc.split("-")[3].substring(0, 2);
            }
        }

        HashMap<String, Object> values = new HashMap<>();
        String tissue_status = "";
        if (!tissue_id.trim().equals("")) {
            tissue_status = BioParser.getTissueStatus(tissue_id);
        }
        values.put("value", tissue_status);
        values.put("required", true);
        additional_attributes.put(attributes_prefix + category_separator + "tissue_status", values);

        /**
         * ***** exp_data_bed_url ******
         */
        String exp_data_bed_url = "";
        values = new HashMap<>();
        String expDataType = "";
        for (String man_attr : gdc_attributes.keySet()) {
            if (man_attr.trim().toLowerCase().equals("gdc__data_type")) {
                expDataType = gdc_attributes.get(man_attr);
                break;
            }
        }
        if (!expDataType.trim().equals("")) {
            if (GDCData.getGDCData2FTPFolderName().containsKey(expDataType.trim().toLowerCase())) {
                String opengdc_data_folder_name = GDCData.getGDCData2FTPFolderName().get(expDataType.trim().toLowerCase());
                exp_data_bed_url = Settings.getOpenGDCFTPRepoProgram(program, false, true) + disease.trim().toLowerCase() + "/" + opengdc_data_folder_name + "/" + aliquot_uuid.trim().toLowerCase() + "-" + suffix_id + "." + Settings.getOpenGDCFTPConvertedDataFormat();
            } else {
                exp_data_bed_url = "";
            }
        }
        values.put("value", exp_data_bed_url);
        values.put("required", true);
        additional_attributes.put(attributes_prefix + category_separator + "exp_data_" + Settings.getOpenGDCFTPConvertedDataFormat() + "_url", values);

        /**
         * ***** opengdc_file_size ******
         */
        values = new HashMap<>();
        URL filesinfo_url_converted = null;
        try {
            filesinfo_url_converted = new URL(Settings.getUpdateTableURL(program, disease.toLowerCase(), GDCData.getGDCData2FTPFolderName().get(manually_curated_dataType.toLowerCase()), false, false));
        } catch (Exception e) {}
        String opengdc_file_size = UpdateGDCData.getUpdateTableAttribute(program.toLowerCase(), disease.toLowerCase(), GDCData.getGDCData2FTPFolderName().get(manually_curated_dataType.toLowerCase()), filesinfo_url_converted, aliquot_uuid.trim().toLowerCase(), "file_size", false);
        values.put("value", opengdc_file_size);
        values.put("required", true);
        additional_attributes.put(attributes_prefix + category_separator + "opengdc_file_size", values);


        /**
         * ***** manually_curated__opengdc_file_md5 ******
         */
        values = new HashMap<>();

        String opengdc_file_md5 = UpdateGDCData.getUpdateTableAttribute(program.toLowerCase(), disease.toLowerCase(), GDCData.getGDCData2FTPFolderName().get(manually_curated_dataType.toLowerCase()), filesinfo_url_converted, aliquot_uuid.trim().toLowerCase(), "md5sum", false);
        values.put("value", opengdc_file_md5);
        values.put("required", true);
        additional_attributes.put(attributes_prefix + category_separator + "opengdc_file_md5", values);



        /**
         * ***** opengdc_id ******
         */
        values = new HashMap<>();
        String opengdcId = "";
        opengdcId = aliquot_uuid.trim().toLowerCase() + "-" + suffix_id;
        values.put("value", opengdcId);
        values.put("required", true);
        additional_attributes.put(attributes_prefix + category_separator + "opengdc_id", values);

        /**
         * ***** data_format ******
         */
        values = new HashMap<>();
        String data_format = "";
        if (!exp_data_bed_url.trim().equals("")) {
            data_format = Settings.getOpenGDCFTPConvertedDataFormat().toUpperCase();
        }
        values.put("value", data_format);
        values.put("required", true);
        additional_attributes.put(attributes_prefix + category_separator + "data_format", values);

        /**
         * ***** exp_metadata_url ******
         * manually_curated_dataType should be replaced with dataType
         */
        values = new HashMap<>();
        values.put("value", Settings.getOpenGDCFTPRepoProgram(program, false, true) + disease.trim().toLowerCase() + "/" + GDCData.getGDCData2FTPFolderName().get(manually_curated_dataType.trim().toLowerCase()) + "/" + aliquot_uuid.trim().toLowerCase() + "-" + suffix_id + "." + Settings.getOpenGDCFTPConvertedDataFormat() + "." + format);
        values.put("required", true);
        additional_attributes.put(attributes_prefix + category_separator + "exp_metadata_url", values);

        /**
         * ***** genome_built ******
         */
        values = new HashMap<>();
        values.put("value", "GRCh38");
        values.put("required", true);
        additional_attributes.put(attributes_prefix + category_separator + "genome_built", values);

        /**
         * ***** opengdc_download_date ******
         */
        values = new HashMap<>();
        String file_uuid = UpdateGDCData.getUpdateTableAttribute(program.toLowerCase(), disease.toLowerCase(), GDCData.getGDCData2FTPFolderName().get(manually_curated_dataType.toLowerCase()), filesinfo_url_converted, aliquot_uuid.trim().toLowerCase(), "file_uuid", false);
        URL filesinfo_url_original = null;
        try {
            filesinfo_url_original = new URL(Settings.getUpdateTableURL(program, disease.toLowerCase(), GDCData.getGDCData2FTPFolderName().get(manually_curated_dataType.toLowerCase()), true, false));
        } catch (Exception e) {}
        String opengdc_download_date = UpdateGDCData.getUpdateTableAttribute(program.toLowerCase(), disease.toLowerCase(), GDCData.getGDCData2FTPFolderName().get(manually_curated_dataType.toLowerCase()), filesinfo_url_original, file_uuid.trim().toLowerCase(), "downloaded_datetime", true);
        values.put("value", opengdc_download_date);
        values.put("required", true);
        additional_attributes.put(attributes_prefix + category_separator + "opengdc_download_date", values);

        return additional_attributes;
    }

    /*private static int getFileSize(URL url) {
        URLConnection conn = null;
        try {
            conn = url.openConnection();
            if(conn instanceof HttpURLConnection) {
                ((HttpURLConnection)conn).setRequestMethod("HEAD");
            }
            conn.getInputStream();
            return conn.getContentLength();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            if(conn instanceof HttpURLConnection) {
                ((HttpURLConnection)conn).disconnect();
            }
        }
    }*/

    public static HashMap<String, HashMap<String, Boolean>> getAdditionalAttributes(String endpoint) {
        HashMap<String, HashMap<String, Boolean>> additionalAttributes = new HashMap<>();
        String additional_attribute_file_path = Settings.getAdditionalMetaAttributesPath();
        File additional_attribute_file = new File(additional_attribute_file_path);
        if (additional_attribute_file.exists()) {
            try {
                InputStream fstream = new FileInputStream(additional_attribute_file);
                DataInputStream in = new DataInputStream(fstream);
                BufferedReader br = new BufferedReader(new InputStreamReader(in));
                String line;
                while ((line = br.readLine()) != null) {
                    if (!line.trim().equals("") && !line.trim().startsWith("#")) {
                        String[] line_split = line.split("\t");
                        String in_file_endpoint = line_split[1];
                        if (in_file_endpoint.trim().toLowerCase().equals(endpoint.trim().toLowerCase())) {
                            String map_name = line_split[0];
                            HashMap<String, Boolean> attributes = new HashMap<>();
                            if (additionalAttributes.containsKey(map_name)) {
                                attributes = additionalAttributes.get(map_name);
                            }
                            String attribute = line_split[2];
                            boolean required = line_split[3].equals("true");
                            attributes.put(attribute, required);
                            additionalAttributes.put(map_name, attributes);
                        }

                    }
                }
                br.close();
                in.close();
                fstream.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return additionalAttributes;
    }

    public static ArrayList<String> getManuallyCuratedAttributesWithNoCases() {
        ArrayList<String> attributes = new ArrayList<>();
        attributes.add("case_id");
        attributes.add("disease_type");
        attributes.add("primary_site");
        attributes.add("demographic.year_of_birth");
        attributes.add("project.program.program_id");
        attributes.add("project.program.name");
        // other gdc attributes
        attributes.add("submitter_id");
        attributes.add("samples.sample_id");
        attributes.add("samples.portions.analytes.aliquots.aliquot_id");
        attributes.add("samples.portions.analytes.aliquots.submitter_id");

        return attributes;
    }

    public static ArrayList<String> getAggregatedAdditionalAttributes() {
        ArrayList<String> attributes = new ArrayList<>();
        attributes.add("file_id");
        attributes.add("file_name");
        attributes.add("file_size");
        attributes.add("analysis.analysis_id");
        attributes.add("analysis.workflow_type");

        return attributes;
    }

    public static ArrayList<HashMap<String, String>> aggregateSameDataTypeInfo(ArrayList<HashMap<String, ArrayList<Object>>> files_info, ArrayList<String> aggregatedAdditionalAttributes) {
        HashMap<String, ArrayList<HashMap<String, ArrayList<Object>>>> aggregated = new HashMap<>();
        //String platform_tmp = "";
        for (HashMap<String, ArrayList<Object>> file_info : files_info) {
            if (file_info != null) {
                if (file_info.containsKey("data_type")) {
                    ArrayList<Object> data_type_list = file_info.get("data_type");
                    ArrayList<HashMap<String, ArrayList<Object>>> values = new ArrayList<>();
                    String data_type = "";
                    for (Object obj : data_type_list) {
                        try {
                            HashMap<String, Object> map = (HashMap<String, Object>) obj;
                            data_type = String.valueOf(map.get("data_type"));
                            break;
                        } catch (Exception e) {
                        }
                    }
                    if (aggregated.containsKey(data_type)) {
                        values = aggregated.get(data_type);
                    }
                    values.add(file_info);
                    aggregated.put(data_type, values);
                    /*if (data_type.trim().toLowerCase().equals("aligned reads")) {
                        if (file_info.containsKey("platform")) {
                            ArrayList<Object> platform_tmp_list = file_info.get("platform");
                            platform_tmp = "";
                            for (Object obj: platform_tmp_list) {
                                try {
                                    HashMap<String, Object> map = (HashMap<String, Object>)obj;
                                    platform_tmp = String.valueOf(map.get("platform"));
                                    break;
                                }
                                catch (Exception e) { }
                            }
                        }
                    }*/
                }
            }
        }

        ArrayList<HashMap<String, String>> compressedMap = new ArrayList<>();
        for (String key : aggregated.keySet()) {
            HashMap<String, String> tmp = new HashMap<>();
            ArrayList<HashMap<String, ArrayList<Object>>> mapList = aggregated.get(key);
            for (HashMap<String, ArrayList<Object>> map : mapList) {
                for (String attribute : map.keySet()) {
                    String value = "";
                    if (tmp.containsKey(attribute)) {
                        value = tmp.get(attribute);
                    }
                    LinkedHashSet<String> values_set = new LinkedHashSet<>();
                    if (!value.trim().equals("")) {
                        String[] value_split = value.split(",");
                        for (String val : value_split) {
                            values_set.add(val);
                        }
                    }
                    ArrayList<Object> value_tmp_list = map.get(attribute);
                    String value_tmp = "";
                    for (Object obj : value_tmp_list) {
                        try {
                            HashMap<String, Object> map_tmp = (HashMap<String, Object>) obj;
                            value_tmp = String.valueOf(map_tmp.get(attribute));
                            values_set.add(value_tmp);
                            break;
                        } catch (Exception e) {
                        }
                    }
                    if (!value_tmp.trim().equals("")) {
                        if (aggregatedAdditionalAttributes.contains(attribute)) {
                            String final_value = "";
                            for (String val : values_set) {
                                final_value = final_value + val + ",";
                            }
                            final_value = final_value.substring(0, final_value.length() - 1);
                            tmp.put(attribute, final_value);
                        } else {
                            tmp.put(attribute, value_tmp);
                        }
                    }
                }
            }

            // platform control
            // if platform does not exist or is empty
            // set the same platform of the Aligned Reads
            /*if (!tmp.containsKey("platform"))
                tmp.put("platform", platform_tmp);
            else {
                if (tmp.get("platform").trim().equals(""))
                    tmp.put("platform", platform_tmp);
            }*/
            // populate compressedMap
            compressedMap.add(tmp);
        }

        return compressedMap;
    }

    public static HashMap<String, HashMap<String, String>> detectRedundantMetadata(HashMap<String, String> meta_map) {
        HashMap<String, HashMap<String, String>> redundantValues = new HashMap<>();

        HashMap<String, ArrayList<String>> mapping_file_attribute = YAMLreader.getMappingAttributes();
        for (String attribute_mapping : mapping_file_attribute.keySet()) {
            if(meta_map.containsKey(attribute_mapping)){
                ArrayList<String> list_attribute_forAllredundantGroup = new ArrayList<>();
                ArrayList<String> reduntant_values_mapping = mapping_file_attribute.get(attribute_mapping);
                list_attribute_forAllredundantGroup.addAll(reduntant_values_mapping);
                list_attribute_forAllredundantGroup.add(attribute_mapping);

                HashMap<String, String> attr_list_mapping_fixed = new HashMap<>();
                for (String attr : list_attribute_forAllredundantGroup) {
                    String value = null;
                    if(meta_map.containsKey(attr))
                        value = meta_map.get(attr);
                    if(value!=null)
                        attr_list_mapping_fixed.put(attr, value);
                }

                for (String attr : attr_list_mapping_fixed.keySet()) {
                    String[] attribute_split_mapping = attr.split("__");
                    String stripped_attribute_mapping = attribute_split_mapping[attribute_split_mapping.length - 1];

                    HashMap<String, String> attr_list_mapping = new HashMap<>();
                    String value_attr = attr_list_mapping_fixed.get(attr);
                    meta_map.remove(attr);
                    if (redundantValues.containsKey(stripped_attribute_mapping+"_"+value_attr.toLowerCase())) {
                        attr_list_mapping = redundantValues.get(stripped_attribute_mapping+"_"+value_attr.toLowerCase());
                    }
                    attr_list_mapping.putAll(attr_list_mapping_fixed);

                    redundantValues.put(stripped_attribute_mapping+"_"+value_attr.toLowerCase(), attr_list_mapping);
                }
            }
        }

        for (String attribute : meta_map.keySet()) {
            String[] attribute_split = attribute.split("__");
            String value = meta_map.get(attribute);
            String stripped_attribute = attribute_split[attribute_split.length - 1];
            HashMap<String, String> attr_list = new HashMap<>();
            if(attribute.contains("input_files"))
                stripped_attribute = attribute;
            if (redundantValues.containsKey(stripped_attribute+"_"+value.toLowerCase())) {
                attr_list = redundantValues.get(stripped_attribute+"_"+value.toLowerCase());
                for (String attr : attr_list.keySet()) {
                    if ((attr_list.get(attr)).toLowerCase().equals(value.toLowerCase())) {
                        attr_list.put(attribute, value);
                        break;
                    }
                }
            } else {
                attr_list.put(attribute, value);
            }
            redundantValues.put(stripped_attribute+"_"+value.toLowerCase(), attr_list);
        }
        return redundantValues;
    }

    public static HashMap<String, String> filterOutRedundantMetadata(HashMap<String, HashMap<String, String>> redundant_map, String program) {
        HashMap<String, String> metadata = new HashMap<>();
        if (program.trim().toLowerCase().equals("tcga")) {
            for (String last_attr : redundant_map.keySet()) {
                ArrayList<String> biospecimen_attrs = new ArrayList<>();
                ArrayList<String> clinical_attrs = new ArrayList<>();
                ArrayList<String> gdc_attrs = new ArrayList<>();
                ArrayList<String> manually_curated_attrs = new ArrayList<>();
                for (String attribute_path : redundant_map.get(last_attr).keySet()) {
                    if (attribute_path.toLowerCase().startsWith("biospecimen"))
                        biospecimen_attrs.add(attribute_path);
                    else if (attribute_path.toLowerCase().startsWith("clinical"))
                        clinical_attrs.add(attribute_path);
                    else if (attribute_path.toLowerCase().startsWith("gdc"))
                        gdc_attrs.add(attribute_path);
                    else if (attribute_path.toLowerCase().startsWith("manually_curated"))
                        manually_curated_attrs.add(attribute_path);
                }

                if (manually_curated_attrs.size() > 0) { // if manually_curated -> no redundancy
                    ArrayList<String> selectedAttributes = selectAttributes(manually_curated_attrs);
                    for (String attr : selectedAttributes)
                        metadata.put(attr, redundant_map.get(last_attr).get(attr));
                }
                else {
                    ArrayList<String> selectedAttributes = null;
                    if (biospecimen_attrs.size()>0 && clinical_attrs.isEmpty() && gdc_attrs.isEmpty()) // biospecimen only
                        selectedAttributes = selectAttributes(biospecimen_attrs);
                    else if (biospecimen_attrs.isEmpty() && clinical_attrs.size()>0 && gdc_attrs.isEmpty()) // clinical only
                        selectedAttributes = selectAttributes(clinical_attrs);
                    else if (biospecimen_attrs.isEmpty() && clinical_attrs.isEmpty() && gdc_attrs.size()>0) // gdc only
                        selectedAttributes = selectAttributes(gdc_attrs);                        
                    else if (biospecimen_attrs.size()>0 && clinical_attrs.size()>0 && gdc_attrs.isEmpty())
                        selectedAttributes = selectAttributes(biospecimen_attrs);
                    else if (biospecimen_attrs.size()>0 && clinical_attrs.size()>0 && gdc_attrs.size()>0)
                        selectedAttributes = selectAttributes(gdc_attrs);
                    else if ((biospecimen_attrs.size()>0 && clinical_attrs.isEmpty() && gdc_attrs.size()>0) || (biospecimen_attrs.isEmpty() && clinical_attrs.size()>0 && gdc_attrs.size()>0)) // (biospecimen and gdc) or (clinical and gdc)
                        selectedAttributes = selectAttributes(gdc_attrs);
                    if (selectedAttributes != null)
                        for (String attr : selectedAttributes)
                            metadata.put(attr, redundant_map.get(last_attr).get(attr));
                }
            }
        }
        else if (program.trim().toLowerCase().equals("target")) {
            // TO-DO
        }
        return metadata;
    }

    public static ArrayList<String> selectAttributes(ArrayList<String> attributes) {
        ArrayList<String> remaining_attributes = new ArrayList<>();
        for (String attr: attributes) {
            //state e annotation sempre eliminati
            if (attr.toLowerCase().endsWith("__state"))
                continue;
            else if (attr.toLowerCase().contains("annotations"))
                continue;
            //1) eliminati perchè vedi punto 2)
            //          else if (attr.toLowerCase().contains("input_files"))
            //              continue;
            //          else if (attr.toLowerCase().contains("cases__project"))
            //              continue;
            //non considera ....analytes__analyte_type. Questo caso comunque è incluso alla fine perchè prendiamo l'attributo più lungo
            //          else if (attr.toLowerCase().contains("analytes") && !attr.toLowerCase().contains("analytes__aliquots")) 
            //              continue;
            else
                remaining_attributes.add(attr);
        }
        //2) input_files, cases__project e associated_entities li dobbiamo considerare solo nel caso rimangano solamente come singolo elemento nella lista. ES:
        /*ORIGINAL attributes
            gdc__cases__project__program__name
          FINAL attributes
            gdc__cases__project__program__name
         * */
        if (remaining_attributes.size() == 1)
            //return remaining_attributes.get(0);
            return remaining_attributes;
        else { //3) se non sono elementi singoli della lista allora andiamo a considerare l'altro. ES: 
            /*ORIGINAL attributes
                gdc__cases__project__disease_type
                gdc__cases__disease_type
              FINAL attributes
                gdc__cases__disease_type
             * */

            //se remaining_attributes è vuota? va nell'else e mi torna attribute = "", quindi lo pongo  a null perchè alla riga 753 controllo se è diverso da null
            ArrayList<String> final_attributes = new ArrayList<>();
            String attribute = null;
            if (remaining_attributes.contains("gdc__cases__disease_type")) {
                attribute = "gdc__cases__disease_type";
                final_attributes.add(attribute);
            }
            else{
                for (String attr: remaining_attributes) {
                    //int attr_size = attr.split("__").length;
                    if (!attr.toLowerCase().contains("associated_entities") && !attr.toLowerCase().contains("cases__project")) {
                        //attribute = attr;
                        final_attributes.add(attr);
                    }
                }
            }
            //return attribute;
            return final_attributes;
        }
    }

    public static HashMap<String, String> renameAttributes(HashMap<String, String> final_map) {
        HashMap<String,String> map = new HashMap<String,String>();
        for (String key: final_map.keySet()) {
            if(key.toLowerCase().startsWith("gdc__analysis__input_files__"))
                map.put(key.toLowerCase().replaceAll("gdc__analysis__input_files__", "gdc__input_files__"), final_map.get(key));
            else if(key.toLowerCase().startsWith("gdc__cases__samples__portions__analytes__aliquots__center__"))
                map.put(key.toLowerCase().replaceAll("gdc__cases__samples__portions__analytes__aliquots__center__", "gdc__center__"), final_map.get(key));
            else if(key.toLowerCase().startsWith("gdc__cases__samples__portions__analytes__aliquots__"))
                map.put(key.toLowerCase().replaceAll("gdc__cases__samples__portions__analytes__aliquots__", "gdc__aliquots__"), final_map.get(key));
            else if(key.toLowerCase().startsWith("gdc__cases__samples__portions__analytes__"))
                map.put(key.toLowerCase().replaceAll("gdc__cases__samples__portions__analytes__", "gdc__analytes__"), final_map.get(key));
            else if(key.toLowerCase().startsWith("gdc__cases__samples__portions__slides__"))
                map.put(key.toLowerCase().replaceAll("gdc__cases__samples__portions__slides__", "gdc__slides__"), final_map.get(key));
            else if(key.toLowerCase().startsWith("gdc__cases__samples__portions__"))
                map.put(key.toLowerCase().replaceAll("gdc__cases__samples__portions__", "gdc__portions__"), final_map.get(key));
            else if(key.toLowerCase().startsWith("gdc__cases__samples__"))
                map.put(key.toLowerCase().replaceAll("gdc__cases__samples__", "gdc__samples__"), final_map.get(key));
            else if(key.toLowerCase().startsWith("gdc__cases__project__program__"))
                map.put(key.toLowerCase().replaceAll("gdc__cases__project__program__", "gdc__program__"), final_map.get(key));
            else if(key.toLowerCase().startsWith("gdc__cases__project__"))
                map.put(key.toLowerCase().replaceAll("gdc__cases__project__", "gdc__project__"), final_map.get(key));
            else if(key.toLowerCase().startsWith("gdc__cases__diagnoses__treatments__"))
                map.put(key.toLowerCase().replaceAll("gdc__cases__diagnoses__treatments__", "gdc__treatments__"), final_map.get(key));
            else if(key.toLowerCase().startsWith("gdc__cases__diagnoses__"))
                map.put(key.toLowerCase().replaceAll("gdc__cases__diagnoses__", "gdc__diagnoses__"), final_map.get(key));
            else if(key.toLowerCase().startsWith("gdc__cases__demographic__"))
                map.put(key.toLowerCase().replaceAll("gdc__cases__demographic__", "gdc__demographic__"), final_map.get(key));
            else if(key.toLowerCase().startsWith("gdc__cases__tissue_source_site__"))
                map.put(key.toLowerCase().replaceAll("gdc__cases__tissue_source_site__", "gdc__tissue_source_site__"), final_map.get(key));
            else if(key.toLowerCase().startsWith("gdc__cases__"))
                map.put(key.toLowerCase().replaceAll("gdc__cases__", "gdc__"), final_map.get(key));
            else 
                map.put(key, final_map.get(key));
        }
        return map;
    }

    /*public static void main(String[] args) {
        System.err.println("Biospecimen sample");
        String biospecimen_xml_path = "/Users/fabio/Downloads/test_gdc_download/ACC-biospecimen/nationwidechildrens.org_biospecimen.TCGA-OR-A5J1.xml";
        HashMap<String, Object> xml_biospecimen_data = getXMLMap(biospecimen_xml_path);
        HashMap<String, String> biospecimen_data = getDataMap(xml_biospecimen_data, null);
        System.err.println("XML Data size: " + xml_biospecimen_data.size());
        System.err.println("Data size: " + biospecimen_data.size()+"\n");
        printMap(biospecimen_data, 0);

        System.err.println("\n#################################################\n");

        System.err.println("Clinical sample");
        String clinical_xml_path = "/Users/fabio/Downloads/test_gdc_download/ACC-clinical/nationwidechildrens.org_clinical.TCGA-OR-A5J1.xml";
        HashMap<String, Object> xml_clinical_data = getXMLMap(clinical_xml_path);
        HashMap<String, String> clinical_data = getDataMap(xml_clinical_data, null);
        System.err.println("XML Data size: " + xml_clinical_data.size());
        System.err.println("Data size: " + clinical_data.size()+"\n");
        printMap(clinical_data, 0);
    }*/
}
