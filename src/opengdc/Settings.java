package opengdc;

import java.io.File;

/**
 *
 * @author fabio
 */
public class Settings {
    
    // debug flag
    private static final boolean DEBUG = false;
    public static boolean isDebug() {
        return DEBUG;
    }
    
    private static final int UPDATE_DAYS = 30;
    public static int getUpdateDays() {
        return UPDATE_DAYS;
    }
	
    // A combination od date and time of day in the form [-]CCYY-MM-DDThh:mm:ss[Z|(+|-)hh:mm]
    private static String FILES_DATETIME = "1989-12-30";
    public static String getFilesDatetime() {
	return FILES_DATETIME;
    }
    public static void setFilesDatetime(String files_datetime) {
        FILES_DATETIME = files_datetime;
    }
    
    // debug references local
//    private static final String DEBUG_TMP = "/Users/eleonora/Downloads/test_gdc_download/tmp/";
//    private static final String DEBUG_APPDATA = "/Users/eleonora/NetBeansProjects/OpenGDC/package/appdata/";
    // debug references server
    private static final String DEBUG_TMP = "/FTP/Software/appdata/tmp/";
    private static final String DEBUG_APPDATA = "/FTP/Software/appdata/";
    
    // build number
    private static final String BUILD_NUMBER = "0001";
    public static String getBuildNumber() {
        return BUILD_NUMBER;
    }
    
    // ****************** tmp dir ******************
    private static final String TMP_DIR = "./appdata/tmp/";
    public static String getTmpDir() {
        String tmpDir = TMP_DIR;
        if (DEBUG) tmpDir = DEBUG_TMP;
        if (!(new File(tmpDir)).exists())
            (new File(tmpDir)).mkdirs();
        return tmpDir;
    }
    // *********************************************
    
    
    // ############### download tab ################
    private static String outputGDCfolder = "";
    public static String getOutputGDCFolder() {
        return outputGDCfolder;
    }
    public static void setOutputGDCFolder(String path) {
        outputGDCfolder = path;
    }
    // #############################################
    
    
    // --------------- convert tab -----------------
    private static String outputConvertedfolder = "";
    public static String getOutputConvertedFolder() {
        return outputConvertedfolder;
    }
    public static void setOutputConvertedFolder(String path) {
        outputConvertedfolder = path;
    }
    
    private static String inputGDCfolder = "";
    public static String getInputGDCFolder() {
        return inputGDCfolder;
    }
    public static void setInputGDCFolder(String path) {
        inputGDCfolder = path;
    }
    // ---------------------------------------------
    
    
    // @@@@@@@@@@@@@@@@@@@@ url @@@@@@@@@@@@@@@@@@@@
    private static final String GDC_DATA_PORTAL_URL = "https://gdc.cancer.gov/";
    public static String getGDCDataPortalURL() {
        return GDC_DATA_PORTAL_URL;
    }
    
    private static final String OPEN_GDC_PAGE_URL = "http://bioinf.iasi.cnr.it/opengdc/";
    public static String getOpenGDCPageURL() {
        return OPEN_GDC_PAGE_URL;
    }
    
    private static final String STATIC_GDC_FTP_REPO_BASE = "ftp://bioinformatics.iasi.cnr.it/opengdc/";
    public static String getStaticOpenGDCFTPRepoBase() {
        return STATIC_GDC_FTP_REPO_BASE;
    }
    
    private static String open_gdc_ftp_repo_base = "ftp://bioinformatics.iasi.cnr.it/opengdc/";
    public static String getOpenGDCFTPRepoBase() {
        return open_gdc_ftp_repo_base;
    }
    public static void setOpenGDCFTPRepoBase(String repo_base) {
        open_gdc_ftp_repo_base = repo_base;
    }
    
    private static final String OPEN_GDC_FTP_REPO_ORIGINAL = "original";
    public static String getOpenGDCFTPRepoOriginal(boolean static_url) {
        if (static_url)
            return getStaticOpenGDCFTPRepoBase()+OPEN_GDC_FTP_REPO_ORIGINAL+"/";
        return getOpenGDCFTPRepoBase()+OPEN_GDC_FTP_REPO_ORIGINAL+"/";
    }
    
    private static final String OPEN_GDC_FTP_REPO_BED_CONVERTED = "bed";
    public static String getOpenGDCFTPRepoBEDConverted(boolean static_url) {
        if (static_url)
            return getStaticOpenGDCFTPRepoBase()+OPEN_GDC_FTP_REPO_BED_CONVERTED+"/";
        return getOpenGDCFTPRepoBase()+OPEN_GDC_FTP_REPO_BED_CONVERTED+"/";
    }
    
    private static final String OPEN_GDC_FTP_REPO_TCGA = "tcga";
    private static final String OPEN_GDC_FTP_REPO_TARGET = "target";
    private static final String OPEN_GDC_FTP_REPO_FM = "fm";
    public static String getOpenGDCFTPRepoProgram(String program, boolean original, boolean static_url) {
        if (program.trim().toLowerCase().contains("tcga")) {
            if (original) return getOpenGDCFTPRepoOriginal(static_url)+OPEN_GDC_FTP_REPO_TCGA+"/";
            return getOpenGDCFTPRepoBEDConverted(static_url)+OPEN_GDC_FTP_REPO_TCGA+"/";
        }
        else if (program.trim().toLowerCase().contains("target")) {
            if (original) return getOpenGDCFTPRepoOriginal(static_url)+OPEN_GDC_FTP_REPO_TARGET+"/";
            return getOpenGDCFTPRepoBEDConverted(static_url)+OPEN_GDC_FTP_REPO_TARGET+"/";
        }
        else if (program.trim().toLowerCase().contains("fm")) {
            if (original) return getOpenGDCFTPRepoOriginal(static_url)+OPEN_GDC_FTP_REPO_FM+"/";
            return getOpenGDCFTPRepoBEDConverted(static_url)+OPEN_GDC_FTP_REPO_FM+"/";
        }
        return "";
    }
    
    private static final String OPEN_GDC_FTP_CONVERTED_DATA_FORMAT = "bed";
    public static String getOpenGDCFTPConvertedDataFormat() {
        return OPEN_GDC_FTP_CONVERTED_DATA_FORMAT;
    }
    // @@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@
    
    
    private static final String MIRBASE_HSA_GFF3_DATA = "./appdata/mirbase/hsa.gff3";
    public static String getMirbaseHsaDataPath() {
        if (DEBUG) return DEBUG_APPDATA+"mirbase/hsa.gff3";
        return MIRBASE_HSA_GFF3_DATA;
    }
    
    private static final String NCBI_DATA = "./appdata/ncbi/ref_GRCh38.p2_top_level.gff3";
    public static String getNCBIDataPath() {
        if (DEBUG) return DEBUG_APPDATA+"ncbi/ref_GRCh38.p2_top_level.gff3";
        return NCBI_DATA;
    }
    
    private static final String HISTORY_NCBI_DATA = "./appdata/ncbi/gene_history.txt";
    public static String getHistoryNCBIDataPath() {
        if (DEBUG) return DEBUG_APPDATA+"ncbi/gene_history.txt";
        return HISTORY_NCBI_DATA;
    }
    
    private static final String GENENAMES_DATA = "./appdata/genenames/hgnc_complete_set.txt";
    public static String getGENENAMESDataPath() {
        if (DEBUG) return DEBUG_APPDATA+"genenames/hgnc_complete_set.txt";
        return GENENAMES_DATA;
    }

    private static final String GENCODE_DATA = "./appdata/gencode/gencode.v22.annotation.gtf";
    public static String getGENCODEDataPath() {
        if (DEBUG) return DEBUG_APPDATA+"gencode/gencode.v22.annotation.gtf";
        return GENCODE_DATA;
    }
    
    private static final String ADDITIONAL_META_ATTRIBUTES = "./appdata/meta/additional_attributes.tsv";
    public static String getAdditionalMetaAttributesPath() {
        if (DEBUG) return DEBUG_APPDATA+"meta/additional_attributes.tsv";
        return ADDITIONAL_META_ATTRIBUTES;
    }
    
    private static final String BIOSPECIMEN_YAML = "./appdata/meta/tcga_biospecimen.yaml";
    public static String getBiospecimenYAML() {
        if (DEBUG) return DEBUG_APPDATA+"meta/tcga_biospecimen.yaml";
        return BIOSPECIMEN_YAML;
    }
    
    private static final String CLINICAL_YAML = "./appdata/meta/tcga_clinical.yaml";
    public static String getClinicalYAML() {
        if (DEBUG) return DEBUG_APPDATA+"meta/tcga_clinical.yaml";
        return CLINICAL_YAML;
    }
    
    private static final String GDC_YAML = "./appdata/meta/tcga_gdc.yaml";
    public static String getGDCYAML() {
        if (DEBUG) return DEBUG_APPDATA+"meta/tcga_gdc.yaml";
        return GDC_YAML;
    }
    
    private static final String UPDATE_TABLE_NAME = "updatetable.txt";
    public static String getUpdateTableName() {
        return UPDATE_TABLE_NAME;
    }
    
    public static String getUpdateTableURL(String program, String disease, String dataType, boolean original, boolean static_url) {
        return getOpenGDCFTPRepoProgram(program, original, static_url) + disease.trim().toLowerCase() + "/" + dataType + "/" + getUpdateTableName();
    }
	
}
