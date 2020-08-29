package opengdc.parser;

import opengdc.GUI;
import opengdc.util.FSUtils;
import opengdc.reader.MaskedSomaticMutationReader;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import opengdc.util.FormatUtils;
import org.apache.commons.io.FileUtils;

/**
 *
 * @author fabio
 */
public class MaskedSomaticMutationParser extends BioParser {

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
               File last_modified =files_out[0];
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
        
        HashMap<String, String> error_inputFile2outputFile = new HashMap<>();
        HashMap<String, String> filesPathConverted = new HashMap<>();
        HashMap<String, Object> uuid_dataMap = new HashMap<>();
        
        File[] files = (new File(inPath)).listFiles();
        HashMap<String, HashSet<String>> fileUUID2aliquotUUIDs = new HashMap<>();
        int progress_counter = 1;
        for (File f: files) {
            if (f.isFile()) {
                String file_uuid = f.getName().split("_")[0];
                String extension = FSUtils.getFileExtension(f);
                if (getAcceptedInputFileFormats().contains(extension) && !getSkipFiles().contains(f.getName().toLowerCase())) {
                    System.err.println("Processing entry " + progress_counter + "/" + acceptedFiles + ": " + f.getName());
                    GUI.appendLog(this.getLogger(), "Processing entry " + progress_counter + "/" + acceptedFiles + ": " + f.getName() + "\n");
                    
                    HashSet<String> aliquot_uuids = MaskedSomaticMutationReader.getUUIDsFromMaf(f.getAbsolutePath());
                    fileUUID2aliquotUUIDs.put(file_uuid, aliquot_uuids);
                    for (String aliquot_uuid: aliquot_uuids) {
                        HashMap<Integer, HashMap<String, String>> uuidData = MaskedSomaticMutationReader.getUUIDDataFromMaf(f.getAbsolutePath(), aliquot_uuid);
                        //System.err.println("data: "+uuidData.size());
                        
                        if (!uuidData.isEmpty()) {
                            /** store entries **/
                            HashMap<Integer, HashMap<Integer, ArrayList<ArrayList<String>>>> dataMapChr = new HashMap<>();
                            if (uuid_dataMap.containsKey(aliquot_uuid))
                                dataMapChr = (HashMap<Integer, HashMap<Integer, ArrayList<ArrayList<String>>>>)uuid_dataMap.get(aliquot_uuid);
                            
                            String suffix_id = this.getOpenGDCSuffix(dataType, false);
                            String filePath = outPath + aliquot_uuid + "-" + suffix_id + "." + this.getFormat();
                            // create file if it does not exist
                            File out_file = new File(filePath);
                            if (!out_file.exists()) {
                                try {
                                    HashSet<String> filePaths = new HashSet<>(filesPathConverted.values());
                                    if (!filePaths.contains(outPath + aliquot_uuid + "." + this.getFormat())) {
                                        Files.write((new File(filePath)).toPath(), (FormatUtils.initDocument(this.getFormat())).getBytes("UTF-8"), StandardOpenOption.CREATE);
                                        filesPathConverted.put(aliquot_uuid, filePath);
                                    }

                                    for (int entry: uuidData.keySet()) {
                                        ArrayList<String> values = new ArrayList<>();
                                        values.add(parseValue(uuidData.get(entry).get("chromosome"), 0));
                                        String start = String.valueOf((int)Double.parseDouble(uuidData.get(entry).get("start_position")));
                                        values.add(parseValue(start, 1));
                                        String end = String.valueOf((int)Double.parseDouble(uuidData.get(entry).get("end_position")));
                                        values.add(parseValue(end, 2));
                                        values.add(parseValue(uuidData.get(entry).get("strand"), 3));
                                        values.add(parseValue(uuidData.get(entry).get("hugo_symbol"), 4));
                                        values.add(parseValue(uuidData.get(entry).get("entrez_gene_id"), 5));
                                        /*values.add(uuidData.get(entry).get("center"));
                                        values.add(uuidData.get(entry).get("ncbi_build"));*/
                                        values.add(parseValue(uuidData.get(entry).get("variant_classification"), 6));
                                        values.add(parseValue(uuidData.get(entry).get("variant_type"), 7));
                                        values.add(parseValue(uuidData.get(entry).get("reference_allele"), 8));
                                        values.add(parseValue(uuidData.get(entry).get("tumor_seq_allele1"), 9));
                                        values.add(parseValue(uuidData.get(entry).get("tumor_seq_allele2"), 10));
                                        values.add(parseValue(uuidData.get(entry).get("dbsnp_rs"), 11));
                                        //values.add(uuidData.get(entry).get("dbsnp_val_status"));
                                        values.add(parseValue(uuidData.get(entry).get("tumor_sample_barcode"), 12));
                                        values.add(parseValue(uuidData.get(entry).get("matched_norm_sample_barcode"), 13));
                                        values.add(parseValue(uuidData.get(entry).get("match_norm_seq_allele1"), 14));
                                        values.add(parseValue(uuidData.get(entry).get("match_norm_seq_allele2"), 15));
                                        /*values.add(uuidData.get(entry).get("tumor_validation_allele1"));
                                        values.add(uuidData.get(entry).get("tumor_validation_allele2"));
                                        values.add(uuidData.get(entry).get("match_norm_validation_allele1"));
                                        values.add(uuidData.get(entry).get("match_norm_validation_allele2"));
                                        values.add(uuidData.get(entry).get("verification_status"));
                                        values.add(uuidData.get(entry).get("validation_status"));
                                        values.add(uuidData.get(entry).get("mutation_status"));
                                        values.add(uuidData.get(entry).get("sequencing_phase"));
                                        values.add(uuidData.get(entry).get("sequence_source"));
                                        values.add(uuidData.get(entry).get("validation_method"));
                                        values.add(uuidData.get(entry).get("score"));
                                        values.add(uuidData.get(entry).get("bam_file"));
                                        values.add(uuidData.get(entry).get("sequencer"));*/
                                        values.add(parseValue(uuidData.get(entry).get("tumor_sample_uuid"), 16));
                                        values.add(parseValue(uuidData.get(entry).get("matched_norm_sample_uuid"), 17));
                                        /*values.add(uuidData.get(entry).get("hgvsc"));
                                        values.add(uuidData.get(entry).get("hgvsp"));
                                        values.add(uuidData.get(entry).get("hgvsp_short"));
                                        values.add(uuidData.get(entry).get("transcript_id"));
                                        values.add(uuidData.get(entry).get("exon_number"));
                                        values.add(uuidData.get(entry).get("t_depth"));
                                        values.add(uuidData.get(entry).get("t_ref_count"));
                                        values.add(uuidData.get(entry).get("t_alt_count"));
                                        values.add(uuidData.get(entry).get("n_depth_count"));
                                        values.add(uuidData.get(entry).get("n_ref_count"));
                                        values.add(uuidData.get(entry).get("n_alt_count"));
                                        values.add(uuidData.get(entry).get("all_effects_count"));
                                        values.add(uuidData.get(entry).get("allele"));
                                        values.add(uuidData.get(entry).get("gene"));
                                        values.add(uuidData.get(entry).get("feature"));
                                        values.add(uuidData.get(entry).get("feature_type"));
                                        values.add(uuidData.get(entry).get("consequence"));
                                        values.add(uuidData.get(entry).get("cdna_position"));
                                        values.add(uuidData.get(entry).get("cds_position"));
                                        values.add(uuidData.get(entry).get("protein_position"));
                                        values.add(uuidData.get(entry).get("amino_acids"));
                                        values.add(uuidData.get(entry).get("codons"));
                                        values.add(uuidData.get(entry).get("existing_variation"));
                                        values.add(uuidData.get(entry).get("allele_num"));
                                        values.add(uuidData.get(entry).get("distance"));
                                        values.add(uuidData.get(entry).get("transcript_strand"));
                                        values.add(uuidData.get(entry).get("symbol"));
                                        values.add(uuidData.get(entry).get("symbol_source"));
                                        values.add(uuidData.get(entry).get("hgnc_id"));
                                        values.add(uuidData.get(entry).get("biotype"));
                                        values.add(uuidData.get(entry).get("canonical"));
                                        values.add(uuidData.get(entry).get("ccds"));
                                        values.add(uuidData.get(entry).get("ensp"));
                                        values.add(uuidData.get(entry).get("swissprot"));
                                        values.add(uuidData.get(entry).get("trembl"));
                                        values.add(uuidData.get(entry).get("uniparc"));
                                        values.add(uuidData.get(entry).get("refseq"));
                                        values.add(uuidData.get(entry).get("sift"));
                                        values.add(uuidData.get(entry).get("polyphen"));
                                        values.add(uuidData.get(entry).get("exon"));
                                        values.add(uuidData.get(entry).get("intron"));
                                        values.add(uuidData.get(entry).get("domains"));
                                        values.add(uuidData.get(entry).get("gmaf"));
                                        values.add(uuidData.get(entry).get("afr_maf"));
                                        values.add(uuidData.get(entry).get("amr_maf"));
                                        values.add(uuidData.get(entry).get("asn_maf"));
                                        values.add(uuidData.get(entry).get("eas_maf"));
                                        values.add(uuidData.get(entry).get("eur_maf"));
                                        values.add(uuidData.get(entry).get("sas_maf"));
                                        values.add(uuidData.get(entry).get("aa_maf"));
                                        values.add(uuidData.get(entry).get("ea_maf"));
                                        values.add(uuidData.get(entry).get("clin_sig"));
                                        values.add(uuidData.get(entry).get("somatic"));
                                        values.add(uuidData.get(entry).get("pubmed"));
                                        values.add(uuidData.get(entry).get("motif_name"));
                                        values.add(uuidData.get(entry).get("motif_pos"));
                                        values.add(uuidData.get(entry).get("high_inf_pos"));
                                        values.add(uuidData.get(entry).get("motif_score_change"));
                                        values.add(uuidData.get(entry).get("impact"));
                                        values.add(uuidData.get(entry).get("pick"));
                                        values.add(uuidData.get(entry).get("variant_class"));
                                        values.add(uuidData.get(entry).get("tsl"));
                                        values.add(uuidData.get(entry).get("hgvs_offset"));
                                        values.add(uuidData.get(entry).get("pheno"));
                                        values.add(uuidData.get(entry).get("minimised"));
                                        values.add(uuidData.get(entry).get("exac_af_af"));
                                        values.add(uuidData.get(entry).get("exac_af_afr"));
                                        values.add(uuidData.get(entry).get("exac_af_amr"));
                                        values.add(uuidData.get(entry).get("exac_af_eas"));
                                        values.add(uuidData.get(entry).get("exac_af_fin"));
                                        values.add(uuidData.get(entry).get("exac_af_nfe"));
                                        values.add(uuidData.get(entry).get("exac_af_oth"));
                                        values.add(uuidData.get(entry).get("exac_af_sas"));
                                        values.add(uuidData.get(entry).get("gene_pheno"));
                                        values.add(uuidData.get(entry).get("filter"));
                                        values.add(uuidData.get(entry).get("src_vcf_id"));
                                        values.add(uuidData.get(entry).get("tumor_bam_uuid"));
                                        values.add(uuidData.get(entry).get("normal_bam_uuid"));
                                        values.add(uuidData.get(entry).get("gdc_validation_status"));
                                        values.add(uuidData.get(entry).get("gdc_valid_somatic"));*/

                                        /**********************************************************************/
                                        /** populate dataMap then sort genomic coordinates and print entries **/
                                        Integer chr_id = Integer.parseInt(parseValue(uuidData.get(entry).get("chromosome"), 0).replaceAll("chr", "").replaceAll("X", "23").replaceAll("Y", "24"));
                                        Integer start_id = Integer.parseInt(parseValue(uuidData.get(entry).get("start_position"), 1));
                                        HashMap<Integer, ArrayList<ArrayList<String>>> dataMapStart = new HashMap<>();
                                        ArrayList<ArrayList<String>> dataList = new ArrayList<>();
                                        if (dataMapChr.containsKey(chr_id)) {
                                            dataMapStart = dataMapChr.get(chr_id);                                        
                                            if (dataMapStart.containsKey(start_id))
                                                dataList = dataMapStart.get(start_id);
                                            dataList.add(values);
                                        }
                                        else
                                            dataList.add(values);
                                        dataMapStart.put(start_id, dataList);
                                        dataMapChr.put(chr_id, dataMapStart);
                                        uuid_dataMap.put(aliquot_uuid, dataMapChr);
                                        /**********************************************************************/

                                        // decomment this line to print entries without sorting genomic coordinates
                                        //Files.write((new File(outPath + aliquot_uuid + "." + this.getFormat())).toPath(), (FormatUtils.createEntry(this.getFormat(), values, getHeader())).getBytes("UTF-8"), StandardOpenOption.APPEND);
                                    }
                                }
                                catch (Exception e) {
                                    error_inputFile2outputFile.put(f.getAbsolutePath(), filePath);
                                    e.printStackTrace();
                                }
                            }
                        }
                    }
                    
                }
            }
            progress_counter++;
        }
        
        printErrorFileLog(error_inputFile2outputFile);
        
        if (!filesPathConverted.isEmpty()) {
            // close documents
            for (String aliquot_uuid: filesPathConverted.keySet()) {
                try {
                    // sort genomic coordinates and print data
                    HashMap<Integer, HashMap<Integer, ArrayList<ArrayList<String>>>> dataMapChr = (HashMap<Integer, HashMap<Integer, ArrayList<ArrayList<String>>>>)uuid_dataMap.get(aliquot_uuid);
                    this.printData((new File(filesPathConverted.get(aliquot_uuid))).toPath(), dataMapChr, this.getFormat(), getHeader(), getAttributesType());
                    Files.write((new File(filesPathConverted.get(aliquot_uuid))).toPath(), (FormatUtils.endDocument(this.getFormat())).getBytes("UTF-8"), StandardOpenOption.APPEND);
                    
                    if (this.isUpdateTableEnabled()) {
                        String file_uuid = null;
                        for (String f_uuid: fileUUID2aliquotUUIDs.keySet()) {
                            if (fileUUID2aliquotUUIDs.get(f_uuid).contains(aliquot_uuid)) {
                                file_uuid = f_uuid;
                                break;
                            }
                        }
                        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSSZ");
    					String file_convertedDate = format.format(new Date()).replaceAll("(.*)(\\d\\d)$", "$1:$2");
                        String updatetable_row = aliquot_uuid + "\t" + file_uuid + "\t" + file_convertedDate + "\t" + FSUtils.getFileChecksum(new File(filesPathConverted.get(aliquot_uuid))) + "\t" + String.valueOf(FileUtils.sizeOf(new File(filesPathConverted.get(aliquot_uuid))) + "\n");
                        Files.write((new File(this.getUpdateTablePath())).toPath(), (updatetable_row).getBytes("UTF-8"), StandardOpenOption.APPEND);
                    }
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
            }
            // write header.schema
            try {
                System.err.println("\n" + "Generating header.schema");
                GUI.appendLog(this.getLogger(), "\n" + "Generating header.schema" + "\n");
                Files.write((new File(outPath + "header.schema")).toPath(), (FormatUtils.generateDataSchema(this.getHeader(), this.getAttributesType())).getBytes("UTF-8"), StandardOpenOption.CREATE);
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
        
        return 0;
    }

    @Override
    public String[] getHeader() {
        String[] header = new String[18];
        header[0] = "chrom";
        header[1] = "start";
        header[2] = "end";
        header[3] = "strand";
        header[4] = "gene_symbol";
        header[5] = "entrez_gene_id";
        header[6] = "variant_classification";
        header[7] = "variant_type";
        header[8] = "reference_allele";
        header[9] = "tumor_seq_allele1";
        header[10] = "tumor_seq_allele2";
        header[11] = "dbsnp_rs";
        header[12] = "tumor_sample_barcode";
        header[13] = "matched_norm_sample_barcode";
        header[14] = "match_norm_seq_allele1";
        header[15] = "match_norm_seq_allele2";
        header[16] = "tumor_sample_uuid";
        header[17] = "matched_norm_sample_uuid";
        return header;
    }

    @Override
    public String[] getAttributesType() {
        String[] attr_type = new String[18];
        attr_type[0] = "STRING";
        attr_type[1] = "LONG";
        attr_type[2] = "LONG";
        attr_type[3] = "CHAR";
        attr_type[4] = "STRING";
        attr_type[5] = "STRING";
        attr_type[6] = "STRING";
        attr_type[7] = "STRING";
        attr_type[8] = "STRING";
        attr_type[9] = "STRING";
        attr_type[10] = "STRING";
        attr_type[11] = "STRING";
        attr_type[12] = "STRING";
        attr_type[13] = "STRING";
        attr_type[14] = "STRING";
        attr_type[15] = "STRING";
        attr_type[16] = "STRING";
        attr_type[17] = "STRING";
        return attr_type;
    }

    @Override
    public void initAcceptedInputFileFormats() {
        this.acceptedInputFileFormats = new HashSet<>();
        this.acceptedInputFileFormats.add(".maf");
    }
    
}
