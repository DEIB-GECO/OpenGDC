package opengdc.parser;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import opengdc.GUI;
import opengdc.util.FSUtils;
import opengdc.util.GDCQuery;
import opengdc.util.MetadataHandler;
import org.apache.commons.io.FileUtils;

/**
 *
 * @author fabio
 */
public class MetadataParserXLSX extends BioParser {
    
    /** TARGET **/

    @Override
    public int convert(String program, String disease, String dataType, String inPath, String outPath) {
        int acceptedFiles = FSUtils.acceptedFilesInFolder(inPath, getAcceptedInputFileFormats());
        System.err.println("Data Amount: " + acceptedFiles + " files" + "\n\n");
        GUI.appendLog(this.getLogger(), "Data Amount: " + acceptedFiles + " files" + "\n\n");
        if (acceptedFiles == 0)
            return 1;

        if (this.isRecoveryEnabled()) {
            // if the output folder is not empty, delete the most recent file
            File folder = new File(outPath);
            File[] files_out = folder.listFiles();
            if (files_out.length != 0) {
                File last_modified = files_out[0];
                long time = 0;
                for (File file : files_out) {
                    if (file.getName().endsWith(this.getFormat()) && !getSkipFiles().contains(file.getName().toLowerCase())) {
                        if (file.lastModified() > time) {  
                            time = file.lastModified();
                            last_modified = file;
                        }
                    }
                }
                System.err.println("File deleted: " + last_modified.getName());
                last_modified.delete();
            }
        }

        HashMap<String, HashMap<String, String>> clinicalBigMap = new HashMap<>();
        HashMap<String, HashMap<String, String>> biospecimenBigMap = new HashMap<>();
        HashMap<String, String> usi2fileUUID = new HashMap<>();
        
        HashSet<String> dataTypes = new HashSet<>();
        dataTypes.add("Gene Expression Quantification");
        dataTypes.add("Copy Number Segment");
        dataTypes.add("Masked Copy Number Segment");
        dataTypes.add("Methylation Beta Value");
        dataTypes.add("Isoform Expression Quantification");
        dataTypes.add("miRNA Expression Quantification");
        dataTypes.add("Masked Somatic Mutation");
        
        File[] files = (new File(inPath)).listFiles();
        int progress_counter = 1;
        for (File f: files) {
            if (f.isFile()) {
                String extension = FSUtils.getFileExtension(f);
                if (getAcceptedInputFileFormats().contains(extension) && !getSkipFiles().contains(f.getName().toLowerCase())) {
                    String file_uuid = f.getName().split("_")[0];
                    System.err.println("Processing entry " + progress_counter + "/" + acceptedFiles + ": " + f.getName());
                    GUI.appendLog(this.getLogger(), "Processing entry " + progress_counter + "/" + acceptedFiles + ": " + f.getName() + "\n");

                    if (f.getName().toLowerCase().contains("clinical")) {                        
                        HashMap<String, HashMap<String, String>> metadata_from_tsv = MetadataHandler.getXLSXMap(this.logPane, f.getAbsolutePath(), "target usi");
                        for (String key: metadata_from_tsv.keySet()) {
                            HashMap<String, String> values = metadata_from_tsv.get(key);
                            if (clinicalBigMap.containsKey(key))
                                values.putAll(clinicalBigMap.get(key));
                            clinicalBigMap.put(key, values);
                            usi2fileUUID.put(key, file_uuid);
                        }
                    }
                    else if (f.getName().toLowerCase().contains("samplematrix")) {                        
                        HashMap<String, HashMap<String, String>> metadata_from_tsv = MetadataHandler.getXLSXMap(this.logPane, f.getAbsolutePath(), "case usi");
                        for (String key: metadata_from_tsv.keySet()) {
                            HashMap<String, String> values = metadata_from_tsv.get(key);
                            if (biospecimenBigMap.containsKey(key))
                                values.putAll(biospecimenBigMap.get(key));
                            biospecimenBigMap.put(key, values);
                            usi2fileUUID.put(key, file_uuid);
                        }
                    }
                }
            }
            progress_counter++;
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
                                /** retrieve aliquot uuid **/
                                HashMap<String, Boolean> additional_attributes_files_tmp = additional_attributes_files.get(metakey);
                                HashMap<String, Boolean> additional_attributes_cases_tmp = additional_attributes_cases.get(metakey);
                                additional_attributes_files_tmp.put("cases.samples.portions.analytes.aliquots.aliquot_id", false);
                                additional_attributes_files_tmp.put("cases.samples.portions.analytes.aliquots.submitter_id", false);
                                additional_attributes_cases_tmp.put("samples.portions.analytes.aliquots.aliquot_id", false);
                                additional_attributes_cases_tmp.put("samples.portions.analytes.aliquots.submitter_id", false);
                                /***********************************/
                                ArrayList<HashMap<String, ArrayList<Object>>> files_info = GDCQuery.retrieveExpInfoFromAttribute("files", "cases.samples.portions.analytes.aliquots.submitter_id", aliquot_brc, dataTypes, new HashSet<>(additional_attributes_files_tmp.keySet()), 0, 0, null);
                                //ArrayList<HashMap<String, String>> aggregated_files_info = MetadataHandler.aggregateSameDataTypeInfo(files_info, MetadataHandler.getAggregatedAdditionalAttributes());
                                ArrayList<HashMap<String, String>> aggregated_files_info = MetadataHandler.aggregateSameDataTypeInfo(files_info, new ArrayList<>(MetadataHandler.getAdditionalAttributes("files").get("manually_curated").keySet()));
                                
                                String aliquot_uuid = "";
                                if (!aggregated_files_info.isEmpty()) {
                                    aliquot_uuid = aggregated_files_info.get(0).get("cases.samples.portions.analytes.aliquots.aliquot_id");
                                    /*for (String k: aggregated_files_info.get(0).keySet())
                                        System.err.println("---------------> "+k+": "+aggregated_files_info.get(0).get(k));
                                    System.err.println("----> aliquot_id: "+aliquot_uuid);*/
                                }

                                if (aliquot_uuid.trim().equals("")) {
                                    HashSet<String> additional_attributes_tmp = new HashSet<>(additional_attributes_cases_tmp.keySet());
                                    files_info = GDCQuery.retrieveExpInfoFromAttribute("cases", "samples.portions.analytes.aliquots.submitter_id", aliquot_brc, dataTypes, additional_attributes_tmp, 0, 0, null);
                                    if (!files_info.isEmpty()) {
                                        HashMap<String, String> files_info_res = new HashMap<>();
                                        for (HashMap<String, ArrayList<Object>> file_info: files_info) {
                                            for (String k: file_info.keySet()) {
                                                for (Object obj: file_info.get(k)) {
                                                    HashMap<String, Object> map = (HashMap<String, Object>)obj;
                                                    for (String kmap: map.keySet()) {
                                                        try {
                                                            boolean contains_submitter_id = false;
                                                            if (kmap.toLowerCase().equals("samples.portions.analytes.aliquots.aliquot_id")) {
                                                                if (map.containsKey("samples.portions.analytes.aliquots.submitter_id"))
                                                                    contains_submitter_id = true;
                                                            }

                                                            if (contains_submitter_id) {
                                                                if (String.valueOf(map.get("submitter_id")).toLowerCase().equals(aliquot_brc.toLowerCase())) {
                                                                    files_info_res.put("samples.portions.analytes.aliquots.aliquot_id", String.valueOf(map.get("aliquot_id")));
                                                                    files_info_res.put("samples.portions.analytes.aliquots.submitter_id", String.valueOf(map.get("submitter_id")));
                                                                    additional_attributes_tmp.remove("samples.portions.analytes.aliquots.submitter_id");
                                                                }
                                                            }
                                                            else {
                                                                String add_attr_curr = "";
                                                                for (String add_attr: additional_attributes_tmp) {
                                                                    if (add_attr.toLowerCase().equals(kmap.toLowerCase())) {
                                                                        files_info_res.put(add_attr, String.valueOf(map.get(kmap)));
                                                                        add_attr_curr = add_attr;
                                                                        break;
                                                                    }
                                                                }
                                                                if (!add_attr_curr.trim().equals(""))
                                                                    additional_attributes_tmp.remove(add_attr_curr);
                                                            }
                                                        }
                                                        catch (Exception e) { }
                                                    }
                                                }

                                            }
                                        }
                                        aliquot_uuid = files_info_res.get("samples.portions.analytes.aliquots.aliquot_id");
                                        aggregated_files_info = new ArrayList<>();
                                        aggregated_files_info.add(files_info_res);
                                    }
                                }

                                if (aliquot_uuid != null) {
                                    if (!aliquot_uuid.equals("")) {
                                        for (HashMap<String, String> file_info: aggregated_files_info) {
                                            if (file_info != null) {
                                                // handle missing required attributes
                                                HashSet<String> missing_required_attributes = new HashSet<>();
                                                HashMap<String, String> manually_curated = new HashMap<>();
                                                HashMap<String, Boolean> attribute2required;
                                                HashMap<String, Boolean> additional_attributes_tmp;

                                                if (file_info.containsKey("samples.portions.analytes.aliquots.submitter_id")) {
                                                    attribute2required = additional_attributes_cases.get(metakey);
                                                    additional_attributes_tmp = additional_attributes_cases_tmp;
                                                }
                                                else { 
                                                    attribute2required = additional_attributes_files.get(metakey);
                                                    additional_attributes_tmp = additional_attributes_files_tmp;
                                                }

                                                ArrayList<String> file_info_sorted = new ArrayList<>(file_info.keySet());
                                                /***********************/
                                                if (file_info_sorted.contains("samples.portions.analytes.aliquots.submitter_id"))
                                                    file_info_sorted.remove(file_info_sorted.indexOf("samples.portions.analytes.aliquots.submitter_id"));
                                                if (file_info_sorted.contains("cases.samples.portions.analytes.aliquots.submitter_id"))
                                                    file_info_sorted.remove(file_info_sorted.indexOf("cases.samples.portions.analytes.aliquots.submitter_id"));
                                                /***********************/
                                                Collections.sort(file_info_sorted);
                                                ArrayList<String> manually_without_cases = MetadataHandler.getManuallyCuratedAttributesWithNoCases();

                                                //start warning missing attribute
                                                for (String attribute: additional_attributes_tmp.keySet()) {
                                                    String attribute_parsed;
                                                    if (manually_without_cases.contains(attribute))
                                                        attribute_parsed = metakey + "__cases__" + attribute.replaceAll("\\.", "__");
                                                    else
                                                        attribute_parsed = metakey + "__" + attribute.replaceAll("\\.", "__");
                                                    if (additional_attributes_tmp.containsKey(attribute) && !file_info.containsKey(attribute) && attribute2required.containsKey(attribute)) {
                                                        if (attribute2required.get(attribute))
                                                            missing_required_attributes.add(attribute_parsed);
                                                    }
                                                } //end warning missing attribute

                                                for (String attribute: file_info_sorted) {
                                                    //String attribute_parsed = FSUtils.stringToValidJavaIdentifier(metakey + "__" + attribute.replaceAll("\\.", "__"));
                                                    String attribute_parsed;
                                                    if (manually_without_cases.contains(attribute))
                                                        attribute_parsed = metakey + "__cases__" + attribute.replaceAll("\\.", "__");
                                                    else
                                                        attribute_parsed = metakey + "__" + attribute.replaceAll("\\.", "__");
                                                    /*************************************************************/
                                                    /** patch for the attribute 'manually_curated__data_format' **/
                                                    if (attribute_parsed.trim().toLowerCase().equals("manually_curated__data_format"))
                                                        attribute_parsed = "manually_curated__source_data_format";
                                                    /*************************************************************/
                                                    String value_parsed = this.checkForNAs(file_info.get(attribute));

                                                    if (!value_parsed.trim().equals(""))
                                                        manually_curated.put(attribute_parsed, value_parsed);
                                                    else { // warning missing data old
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
                                                    if (mcattr.toLowerCase().contains("manually_curated__data_type")) {
                                                        manually_curated_data_type = manually_curated.get(mcattr);
                                                        break;
                                                    }
                                                }
                                                
                                                // create a suffix to append to the aliquot id
                                                String suffix_id = this.getOpenGDCSuffix(manually_curated_data_type, false);

                                                HashMap<String, HashMap<String, Object>> additional_manually_curated = MetadataHandler.getAdditionalManuallyCuratedAttributes(program, disease, dataType, this.getFormat(), aliquot_uuid, aliquot_brc, biospecimenBigMap.get(case_usi), clinicalBigMap.get(case_usi), manually_curated, suffix_id, manually_curated_data_type);
                                                if (!additional_manually_curated.isEmpty()) {
                                                    for (String attr: additional_manually_curated.keySet()) {
                                                        //String attribute_parsed = FSUtils.stringToValidJavaIdentifier(attr);
                                                        String attribute_parsed = attr;
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
                                                    FileOutputStream fos = new FileOutputStream(out_file);
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
                                                        manually_curated.put("manually_curated__audit_warning", "["+missed_attributes_list.substring(0, missed_attributes_list.length()-2)+"]");
                                                    }

                                                    // if (!manually_curated_data_type.equals("")) {
                                                    // sort and print manually_curated attributes
                                                    ArrayList<String> manually_curated_attributes_sorted = new ArrayList<>(manually_curated.keySet());
                                                    Collections.sort(manually_curated_attributes_sorted);
                                                    for (String attr: manually_curated_attributes_sorted)
                                                        out.println(attr + "\t" + manually_curated.get(attr));
                                                    // }

                                                    out.close();
                                                    fos.close();
                                                    
                                                    if (this.isUpdateTableEnabled()) {
                                                    	SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSSZ");
                                    					String file_convertedDate = format.format(new Date()).replaceAll("(.*)(\\d\\d)$", "$1:$2"); 
                                                        String updatetable_row = aliquot_uuid + "\t" + usi2fileUUID.get(case_usi) + "\t" + file_convertedDate + "\t" + FSUtils.getFileChecksum(out_file) + "\t" + String.valueOf(FileUtils.sizeOf(out_file) + "\n");
                                                        Files.write((new File(this.getUpdateTablePath())).toPath(), (updatetable_row).getBytes("UTF-8"), StandardOpenOption.APPEND);
                                                    }
                                                }
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
