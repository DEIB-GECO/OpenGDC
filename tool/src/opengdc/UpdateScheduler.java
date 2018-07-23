/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package opengdc;

import java.util.Calendar;
import java.util.Timer;
import java.util.concurrent.TimeUnit;

/**
 *
 * @author fabio
 */
public class UpdateScheduler {
    
    //private static final String FTP_ROOT = "/FTP/ftp-root/update_prova/";
    private static final String FTP_ROOT = "/Users/fabio/Downloads/update_prova/";
    private static final String FTP_REPO = "ftp://150.146.100.179/update_prova/";
    
    public static void main(String[] args) {
        Settings.setOpenGDCFTPRepoBase(FTP_REPO);
        Timer timer = new java.util.Timer();
        Calendar date = Calendar.getInstance();
        date.set(Calendar.DAY_OF_WEEK, Calendar.TUESDAY);
        date.set(Calendar.HOUR, 1);
        date.set(Calendar.MINUTE, 0);
        date.set(Calendar.SECOND, 0);
        timer.schedule(new UpdateTask(FTP_ROOT), date.getTime(), TimeUnit.DAYS.toMillis(Settings.getUpdateDays()));
    }
    
}
