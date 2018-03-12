/*
 * Application: OpenGDC
 * Version: 1.0
 * Authors: Fabio Cumbo (1,2), Eleonora Cappelli (1,2), Emanuel Weitschek (1,3)
 * Organizations: 
 * 1. Institute for Systems Analysis and Computer Science "Antonio Ruberti" - National Research Council of Italy, Rome, Italy
 * 2. Department of Engineering - Third University of Rome, Rome, Italy
 * 3. Department of Engineering - Uninettuno International University, Rome, Italy
 */
package opengdc.parser;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import opengdc.GUI;
import opengdc.util.FSUtils;
import opengdc.util.GDCQuery;
import opengdc.util.MetadataHandler;

/**
 *
 * @author fabio
 */
public class MetadataParserXLSX extends BioParser {
    
    /** FM **/
    
    @Override
    public int convert(String program, String disease, String dataType, String inPath, String outPath) {
        int acceptedFiles = FSUtils.acceptedFilesInFolder(inPath, getAcceptedInputFileFormats());
        System.err.println("Data Amount: " + acceptedFiles + " files" + "\n\n");
        GUI.appendLog(this.getLogger(), "Data Amount: " + acceptedFiles + " files" + "\n\n");
        
        if (acceptedFiles == 0)
            return 1;
        
        // if the output folder is not empty, delete the most recent file
        File folder = new File(outPath);
        File[] files_out = folder.listFiles();
        if (files_out.length != 0) {
           File last_modified =files_out[0];
           long time = 0;
           for (File file : files_out) {
              if (file.getName().endsWith(this.getFormat())) {
                 if (file.lastModified() > time) {  
                    time = file.lastModified();
                    last_modified = file;
                 }
              }
           }
           System.err.println("File deleted: " + last_modified.getName());
           last_modified.delete();
        }
        
        HashMap<String, HashMap<String, String>> clinicalBigMap = new HashMap<>();
        HashMap<String, HashMap<String, String>> biospecimenBigMap = new HashMap<>();
        
        File[] files = (new File(inPath)).listFiles();
        for (File f: files) {
            if (f.isFile()) {
                String extension = FSUtils.getFileExtension(f);
                if (getAcceptedInputFileFormats().contains(extension)) {
                    System.err.println("Processing " + f.getName());
                    GUI.appendLog(this.getLogger(), "Processing " + f.getName() + "\n");
                    
                    if (f.getName().toLowerCase().contains("clinical")) {                        
                        HashMap<String, HashMap<String, String>> metadata_from_tsv = MetadataHandler.getXLSXMap(this.logPane, f.getAbsolutePath(), "target usi");
                        for (String key: metadata_from_tsv.keySet()) {
                            HashMap<String, String> values = metadata_from_tsv.get(key);
                            if (clinicalBigMap.containsKey(key))
                                values.putAll(clinicalBigMap.get(key));
                            clinicalBigMap.put(key, values);
                        }
                    }
                    else if (f.getName().toLowerCase().contains("samplematrix")) {                        
                        HashMap<String, HashMap<String, String>> metadata_from_tsv = MetadataHandler.getXLSXMap(this.logPane, f.getAbsolutePath(), "case usi");
                        for (String key: metadata_from_tsv.keySet()) {
                            HashMap<String, String> values = metadata_from_tsv.get(key);
                            if (biospecimenBigMap.containsKey(key))
                                values.putAll(biospecimenBigMap.get(key));
                            biospecimenBigMap.put(key, values);
                        }
                    }
                }
            }
        }
        
        if (!biospecimenBigMap.isEmpty()) {
            HashMap<String, HashMap<String, Boolean>> additional_attributes_files = MetadataHandler.getAdditionalAttributes("files");
            HashMap<String, HashMap<String, Boolean>> additional_attributes_cases = MetadataHandler.getAdditionalAttributes("cases");
            HashMap<String, HashSet<String>> caseusi2aliquots = retrieveAliquotsBRCFromCaseUSI(biospecimenBigMap);
            
            System.err.println("CASE USI #: "+caseusi2aliquots.size());
            
            for (String case_usi: caseusi2aliquots.keySet()) {
                try {
                    // retrieve biospecimen
                    ArrayList<String> biospecimen_sorted = new ArrayList<>(biospecimenBigMap.get(case_usi).keySet());
                    Collections.sort(biospecimen_sorted);

                    // retrieve clinical
                    ArrayList<String> clinical_sorted = new ArrayList<>();
                    try {
                        clinical_sorted = new ArrayList<>(clinicalBigMap.get(case_usi).keySet());
                        Collections.sort(clinical_sorted);
                    }
                    catch (Exception e) { }
                    
                    for (String aliquot_brc: caseusi2aliquots.get(case_usi)) {
                        // generate manually curated metadata
                        if (!additional_attributes_files.isEmpty() || !additional_attributes_cases.isEmpty()) {
                            ArrayList<String> additional_attributes_sorted = new ArrayList<>(additional_attributes_files.keySet());
                            Collections.sort(additional_attributes_sorted);
                            for (String metakey: additional_attributes_sorted) {

                                /** just to retrieve aliquot uuid **/
                                HashMap<String, Boolean> additional_attributes_files_tmp = additional_attributes_files.get(metakey);
                                HashMap<String, Boolean> additional_attributes_cases_tmp = additional_attributes_files.get(metakey);
                                additional_attributes_files_tmp.put("cases.samples.portions.analytes.aliquots.aliquot_id", false);
                                additional_attributes_cases_tmp.put("samples.portions.analytes.aliquots.aliquot_id", false);
                                /***********************************/
                                ArrayList<HashMap<String, String>> files_info = GDCQuery.retrieveExpInfoFromAttribute("files", "cases.samples.portions.analytes.aliquots.submitter_id", aliquot_brc, new HashSet<>(additional_attributes_files_tmp.keySet()), 0, 0, null);
                                ArrayList<HashMap<String, String>> aggregated_files_info = MetadataHandler.aggregateSameDataTypeInfo(files_info, MetadataHandler.getAggregatedAdditionalAttributes());
                                
                                String aliquot_uuid = "";
                                if (!aggregated_files_info.isEmpty())
                                    aliquot_uuid = aggregated_files_info.get(0).get("cases.samples.portions.analytes.aliquots.aliquot_id");
                                
                                if (aliquot_uuid.trim().equals("")) {
                                    files_info = GDCQuery.retrieveExpInfoFromAttribute("cases", "samples.portions.analytes.aliquots.submitter_id", aliquot_brc, new HashSet<>(additional_attributes_cases_tmp.keySet()), 0, 0, null);
                                    if (!files_info.isEmpty())
                                        aliquot_uuid = files_info.get(0).get("samples.portions.analytes.aliquots.aliquot_id");
                                }

                                if (!aliquot_uuid.equals("")) {
                                    for (HashMap<String, String> file_info: aggregated_files_info) {
                                        if (file_info != null) {
                                            // handle missing required attributes
                                            HashSet<String> missing_required_attributes = new HashSet<>();
                                            HashMap<String, String> manually_curated = new HashMap<>();
                                            HashMap<String, Boolean> attribute2required = additional_attributes_files.get(metakey);
                                            ArrayList<String> file_info_sorted = new ArrayList<>(file_info.keySet());
                                            /***********************/
                                            file_info_sorted.remove(file_info_sorted.indexOf("samples.portions.analytes.aliquots.aliquot_id"));
                                            /***********************/
                                            Collections.sort(file_info_sorted);
                                            for (String attribute: file_info_sorted) {
                                                String attribute_parsed = FSUtils.stringToValidJavaIdentifier(metakey + "__" + attribute.replaceAll("\\.", "__"));
                                                /*************************************************************/
                                                /** patch for the attribute 'manually_curated__data_format' **/
                                                if (attribute_parsed.trim().toLowerCase().equals("manually_curated__data_format"))
                                                    attribute_parsed = "manually_curated__source_data_format";
                                                /*************************************************************/
                                                String value_parsed = this.checkForNAs(file_info.get(attribute));
                                                if (!value_parsed.trim().equals(""))
                                                    manually_curated.put(attribute_parsed, value_parsed);
                                                else {
                                                    for (String attr: attribute2required.keySet()) {
                                                        if (attr.toLowerCase().equals(attribute.toLowerCase())) {
                                                            if (attribute2required.get(attr)) // if attribute is required
                                                                missing_required_attributes.add(attribute_parsed);
                                                        }
                                                    }
                                                }
                                            }

                                            // generate additional manually curated metadata
                                            String manually_curated_data_type = "";
                                            for (String mcattr: manually_curated.keySet()) {
                                                if (mcattr.toLowerCase().contains("data_type")) {
                                                    manually_curated_data_type = manually_curated.get(mcattr);
                                                    break;
                                                }
                                            }
                                            // create a suffix to append to the aliquot id
                                            String suffix_id = this.getOpenGDCSuffix(manually_curated_data_type, false);

                                            HashMap<String, HashMap<String, Object>> additional_manually_curated = MetadataHandler.getAdditionalManuallyCuratedAttributes(program, disease, dataType, this.getFormat(), aliquot_uuid, biospecimenBigMap.get(case_usi), clinicalBigMap.get(case_usi), manually_curated, suffix_id);
                                            if (!additional_manually_curated.isEmpty()) {
                                                for (String attr: additional_manually_curated.keySet()) {
                                                    String attribute_parsed = FSUtils.stringToValidJavaIdentifier(attr);
                                                    HashMap<String, Object> values = additional_manually_curated.get(attr);
                                                    if (!values.isEmpty()) {
                                                        String value_parsed = checkForNAs((String)additional_manually_curated.get(attr).get("value"));
                                                        if (!value_parsed.trim().equals(""))
                                                            manually_curated.put(attribute_parsed, value_parsed);
                                                        else {
                                                            if ((Boolean)additional_manually_curated.get(attr).get("required")) // if attribute is required
                                                                missing_required_attributes.add(attr);
                                                        }
                                                    }
                                                }
                                            }
                                            
                                            // create file if it does not exist
                                            File out_file = new File(outPath + aliquot_uuid.toLowerCase() + "-" + suffix_id + "." + this.getFormat());
                                            if (!out_file.exists()) {
                                                FileOutputStream fos = new FileOutputStream(outPath + aliquot_uuid.toLowerCase() + "-" + suffix_id + "." + this.getFormat());
                                                PrintStream out = new PrintStream(fos);

                                                // biospecimen
                                                for (String attribute: biospecimen_sorted) {
                                                    String[] headers = attribute.split(MetadataHandler.__OPENGDCSEP__);
                                                    String attribute_parsed = "";
                                                    for (String h: headers)
                                                        attribute_parsed += FSUtils.stringToValidJavaIdentifier(h) + "__";
                                                    attribute_parsed = "biospecimen__" + attribute_parsed.substring(0, attribute_parsed.length()-2);
                                                    String value_parsed = checkForNAs(biospecimenBigMap.get(case_usi).get(attribute));
                                                    if (!value_parsed.trim().equals(""))
                                                        out.println(attribute_parsed + "\t" + value_parsed);
                                                }

                                                // clinical
                                                for (String attribute: clinical_sorted) {
                                                    String[] headers = attribute.split(MetadataHandler.__OPENGDCSEP__);
                                                    String attribute_parsed = "";
                                                    for (String h: headers)
                                                        attribute_parsed += FSUtils.stringToValidJavaIdentifier(h) + "__";
                                                    attribute_parsed = "clinical__" + attribute_parsed.substring(0, attribute_parsed.length()-2);
                                                    String value_parsed = checkForNAs(clinicalBigMap.get(case_usi).get(attribute));
                                                    if (!value_parsed.trim().equals(""))
                                                        out.println(attribute_parsed + "\t" + value_parsed);
                                                }

                                                // generate audit_warning
                                                if (!missing_required_attributes.isEmpty()) {
                                                    String missed_attributes_list = "";
                                                    for (String ma: missing_required_attributes)
                                                        missed_attributes_list += ma+", ";
                                                    manually_curated.put("manually_curated__audit_warning", "missed the following required metadata: ["+missed_attributes_list.substring(0, missed_attributes_list.length()-2)+"]");
                                                }

                                                if (!manually_curated_data_type.equals("")) {
                                                    // sort and print manually_curated attributes
                                                    ArrayList<String> manually_curated_attributes_sorted = new ArrayList<>(manually_curated.keySet());
                                                    Collections.sort(manually_curated_attributes_sorted);
                                                    for (String attr: manually_curated_attributes_sorted)
                                                        out.println(attr + "\t" + manually_curated.get(attr));
                                                }

                                                out.close();
                                                fos.close();
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
            }           
                        
        }
        
        return 0;
    }    

    private HashMap<String, HashSet<String>> retrieveAliquotsBRCFromCaseUSI(HashMap<String, HashMap<String, String>> bigMap) {
        HashMap<String, HashSet<String>> result = new HashMap<>();
        for (String case_usi: bigMap.keySet()) {
            HashSet<String> aliquots = new HashSet<>();
            for (String attribute: bigMap.get(case_usi).keySet()) {
                String value = bigMap.get(case_usi).get(attribute);
                String[] value_comma_split = value.trim().split(",");
                for (String v: value_comma_split) {
                    if (v.toLowerCase().trim().startsWith("target")) {
                        String[] v_dash_split = v.trim().split("-");
                        if (v_dash_split.length == 5) // aliquot brc size
                            aliquots.add(v);
                    }
                }
            }
            result.put(case_usi, aliquots);
        }
        return result;
    }
    
    @Override
    public String[] getHeader() {
        return null;
    }

    @Override
    public String[] getAttributesType() {
        return null;
    }

    @Override
    public void initAcceptedInputFileFormats() {
        this.acceptedInputFileFormats = new HashSet<>();
        this.acceptedInputFileFormats.add(".xlsx");
    }
    
}