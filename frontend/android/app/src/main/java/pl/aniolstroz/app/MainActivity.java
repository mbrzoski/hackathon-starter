package pl.aniolstroz.app;

import android.Manifest;
import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.WindowManager;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.getcapacitor.BridgeActivity;

/**
 * The Angular app in a WebView (AND-01). Native only where the browser is not enough (AND-04, AND-08): the screen stays
 * on, the microphone permission is asked for before the page calls getUserMedia, and the app pins itself to the
 * screen (kiosk, can be switched off in res/values/kiosk.xml). It never touches the phone line (AND-03, AND-06).
 */
public class MainActivity extends BridgeActivity {

    private static final int MICROPHONE_REQUEST = 1;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.RECORD_AUDIO}, MICROPHONE_REQUEST);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getResources().getBoolean(R.bool.kiosk) && !isPinned()) {
            try {
                startLockTask(); // asks once to pin the app; a device-owner setup would pin without asking
            } catch (IllegalStateException | SecurityException e) {
                // Pinning not allowed on this device: the app still works, only not pinned.
            }
        }
    }

    private boolean isPinned() {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        return manager != null && manager.getLockTaskModeState() != ActivityManager.LOCK_TASK_MODE_NONE;
    }
}
