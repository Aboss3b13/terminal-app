package ch.xknight.terminalapp;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.Vector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final String PREFS = "terminal_app_settings";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private LinearLayout root;
    private LinearLayout content;
    private TextView output;
    private EditText hostInput;
    private EditText userInput;
    private EditText passwordInput;
    private EditText commandInput;
    private EditText pathInput;
    private EditText codexInput;
    private EditText codexPromptInput;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        buildUi();
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private void buildUi() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(16), dp(18), dp(12));
        root.setBackgroundColor(color("app_bg"));

        TextView title = text("Terminal App", 30, "ink", true);
        TextView subtitle = text("Mobile control panel for your VPS", 14, "muted", false);
        root.addView(title);
        root.addView(subtitle);

        LinearLayout connection = panel();
        hostInput = input("Host", prefs.getString("host", "62.171.166.27"), false);
        userInput = input("User", prefs.getString("user", "abbas"), false);
        passwordInput = input("Password", prefs.getString("password", ""), true);
        connection.addView(hostInput);
        connection.addView(userInput);
        connection.addView(passwordInput);
        connection.addView(button("Save Connection", v -> saveConnection(), false));
        root.addView(connection);

        HorizontalScrollView tabsScroll = new HorizontalScrollView(this);
        tabsScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.addView(button("Commands", v -> showCommands(), false));
        tabs.addView(button("Files", v -> showFiles(), false));
        tabs.addView(button("Codex", v -> showCodex(), true));
        tabsScroll.addView(tabs);
        root.addView(tabsScroll);

        ScrollView mainScroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        mainScroll.addView(content);
        root.addView(mainScroll, new LinearLayout.LayoutParams(-1, 0, 1));

        output = text("", 13, "ink", false);
        output.setTypeface(Typeface.MONOSPACE);
        output.setPadding(dp(12), dp(12), dp(12), dp(12));
        output.setBackgroundColor(0xFFEFF4EF);
        root.addView(output, new LinearLayout.LayoutParams(-1, dp(150)));

        setContentView(root);
        showCommands();
    }

    private void showCommands() {
        content.removeAllViews();
        content.addView(sectionTitle("Quick Commands"));
        addCommandButton("Server Status", "hostname && uptime && free -h");
        addCommandButton("Disk Usage", "df -h && du -sh ~ 2>/dev/null");
        addCommandButton("Docker", "docker ps --format 'table {{.Names}}\\t{{.Status}}\\t{{.Ports}}' 2>/dev/null || echo 'Docker not available'");
        addCommandButton("Processes", "ps aux --sort=-%mem | head -15");
        addCommandButton("Update Packages", "sudo apt update && sudo apt list --upgradable");

        commandInput = input("Custom command", "ls -la", false);
        commandInput.setSingleLine(false);
        commandInput.setMinLines(2);
        content.addView(commandInput);
        content.addView(button("Run Command", v -> runCommand(commandInput.getText().toString()), true));
    }

    private void showFiles() {
        content.removeAllViews();
        content.addView(sectionTitle("Files"));
        pathInput = input("Remote path", prefs.getString("path", "/home/abbas"), false);
        content.addView(pathInput);
        content.addView(button("List Path", v -> listFiles(pathInput.getText().toString()), true));
        addCommandButton("Home Folder", "ls -lah /home/abbas");
        addCommandButton("Project Folders", "find /home/abbas -maxdepth 2 -type d | sort | head -80");
    }

    private void showCodex() {
        content.removeAllViews();
        content.addView(sectionTitle("Codex"));
        content.addView(text("Run Codex on the VPS from a mobile prompt. The command stays configurable in case your VPS uses another install path.", 14, "muted", false));
        codexPromptInput = input("Prompt for Codex", "Check this server and suggest the next cleanup steps.", false);
        codexPromptInput.setSingleLine(false);
        codexPromptInput.setMinLines(3);
        content.addView(codexPromptInput);
        codexInput = input("Codex exec command", prefs.getString("codex", "codex exec --dangerously-bypass-approvals-and-sandbox --ask-for-approval never -C ~/codex-workspace"), false);
        codexInput.setSingleLine(false);
        codexInput.setMinLines(2);
        content.addView(codexInput);
        content.addView(button("Run Codex Prompt", v -> {
            prefs.edit().putString("codex", codexInput.getText().toString()).apply();
            String prompt = codexPromptInput.getText().toString().trim();
            if (prompt.isEmpty()) {
                append("Enter a Codex prompt first.");
                return;
            }
            runCommand("mkdir -p ~/codex-workspace && " + codexInput.getText().toString() + " " + shellQuote(prompt));
        }, true));
        addCommandButton("Check Codex Install", "command -v codex && codex --version || echo 'Codex CLI not found'");
        addCommandButton("Open Codex Workspace", "mkdir -p ~/codex-workspace && cd ~/codex-workspace && pwd && ls -la");
    }

    private void addCommandButton(String label, String command) {
        content.addView(button(label, v -> runCommand(command), false));
    }

    private void saveConnection() {
        prefs.edit()
                .putString("host", hostInput.getText().toString().trim())
                .putString("user", userInput.getText().toString().trim())
                .putString("password", passwordInput.getText().toString())
                .apply();
        Toast.makeText(this, "Connection saved on this device", Toast.LENGTH_SHORT).show();
    }

    private void runCommand(String command) {
        if (command.trim().isEmpty()) {
            append("No command entered.");
            return;
        }
        saveConnection();
        append("$ " + command);
        executor.execute(() -> {
            Session session = null;
            ChannelExec channel = null;
            try {
                session = openSession();
                channel = (ChannelExec) session.openChannel("exec");
                channel.setCommand(command);
                channel.setInputStream(null);
                ByteArrayOutputStream err = new ByteArrayOutputStream();
                channel.setErrStream(err);
                InputStream in = channel.getInputStream();
                channel.connect(10000);
                String result = readAll(in);
                while (!channel.isClosed()) {
                    Thread.sleep(120);
                }
                String errors = err.toString(StandardCharsets.UTF_8.name()).trim();
                int exit = channel.getExitStatus();
                append(result.trim().isEmpty() ? "(no output)" : result.trim());
                if (!errors.isEmpty()) {
                    append(errors);
                }
                append("exit " + exit);
            } catch (Exception ex) {
                append("Error: " + ex.getMessage());
            } finally {
                if (channel != null) channel.disconnect();
                if (session != null) session.disconnect();
            }
        });
    }

    private void listFiles(String path) {
        saveConnection();
        prefs.edit().putString("path", path).apply();
        append("sftp> ls " + path);
        executor.execute(() -> {
            Session session = null;
            ChannelSftp sftp = null;
            try {
                session = openSession();
                sftp = (ChannelSftp) session.openChannel("sftp");
                sftp.connect(10000);
                Vector<?> entries = sftp.ls(path);
                StringBuilder builder = new StringBuilder();
                for (Object entry : entries) {
                    ChannelSftp.LsEntry item = (ChannelSftp.LsEntry) entry;
                    builder.append(item.getAttrs().isDir() ? "[dir]  " : "       ");
                    builder.append(item.getFilename()).append('\n');
                }
                append(builder.toString().trim());
            } catch (Exception ex) {
                append("Error: " + ex.getMessage());
            } finally {
                if (sftp != null) sftp.disconnect();
                if (session != null) session.disconnect();
            }
        });
    }

    private Session openSession() throws Exception {
        String host = hostInput.getText().toString().trim();
        String user = userInput.getText().toString().trim();
        String password = passwordInput.getText().toString();
        if (host.isEmpty() || user.isEmpty() || password.isEmpty()) {
            throw new IllegalStateException("Host, user, and password are required.");
        }
        JSch jsch = new JSch();
        Session session = jsch.getSession(user, host, 22);
        session.setPassword(password);
        Properties config = new Properties();
        config.put("StrictHostKeyChecking", "no");
        session.setConfig(config);
        session.connect(12000);
        return session;
    }

    private String readAll(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        return out.toString(StandardCharsets.UTF_8.name());
    }

    private String shellQuote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    private void append(String message) {
        runOnUiThread(() -> {
            String old = output.getText().toString();
            output.setText(old.isEmpty() ? message : old + "\n\n" + message);
        });
    }

    private LinearLayout panel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(12), dp(12), dp(12), dp(12));
        panel.setBackgroundColor(color("panel"));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(14), 0, dp(12));
        panel.setLayoutParams(params);
        return panel;
    }

    private TextView sectionTitle(String label) {
        TextView title = text(label, 20, "ink", true);
        title.setPadding(0, dp(14), 0, dp(8));
        return title;
    }

    private EditText input(String hint, String value, boolean password) {
        EditText edit = new EditText(this);
        edit.setHint(hint);
        edit.setText(value);
        edit.setSingleLine(true);
        edit.setTextColor(color("ink"));
        edit.setHintTextColor(color("muted"));
        edit.setTextSize(15);
        edit.setPadding(dp(10), dp(8), dp(10), dp(8));
        edit.setImeOptions(EditorInfo.IME_ACTION_DONE);
        edit.setInputType(password ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD : InputType.TYPE_CLASS_TEXT);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(4), 0, dp(8));
        edit.setLayoutParams(params);
        return edit;
    }

    private Button button(String label, View.OnClickListener listener, boolean primary) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(14);
        button.setGravity(Gravity.CENTER);
        button.setTextColor(primary ? 0xFFFFFFFF : color("ink"));
        button.setBackgroundColor(primary ? color("green") : 0xFFE7EDE7);
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(48));
        params.setMargins(0, dp(5), dp(8), dp(5));
        button.setLayoutParams(params);
        return button;
    }

    private TextView text(String value, int sp, String colorName, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color(colorName));
        view.setLineSpacing(0, 1.08f);
        if (bold) {
            view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }
        return view;
    }

    private int color(String name) {
        int id = getResources().getIdentifier(name, "color", getPackageName());
        return getResources().getColor(id, getTheme());
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
