package ch.xknight.terminalapp;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
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
    private SharedPreferences prefs;
    private LinearLayout root;
    private LinearLayout body;
    private TextView status;
    private TextView terminal;
    private EditText hostInput;
    private EditText userInput;
    private EditText passwordInput;
    private EditText codexPromptInput;
    private EditText commandInput;
    private EditText pathInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        buildShell();
        showHome();
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private void buildShell() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(14), dp(18), dp(10));
        root.setBackgroundColor(color("app_bg"));

        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));

        status = label("Ready", 12, "muted", false);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, dp(8), 0, 0);
        root.addView(status, new LinearLayout.LayoutParams(-1, -2));

        setContentView(root);
    }

    private void showHome() {
        body.removeAllViews();
        terminal = null;

        TextView title = label("VPS", 34, "ink", true);
        TextView subtitle = label(connectionLine(), 14, "muted", false);
        body.addView(title);
        body.addView(subtitle);

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(2);
        grid.setPadding(0, dp(18), 0, 0);
        body.addView(grid, new LinearLayout.LayoutParams(-1, -2));

        appIcon(grid, "Codex", "AI agent", ">", "green", v -> showCodex());
        appIcon(grid, "Terminal", "Run commands", "$", "ink", v -> showTerminal());
        appIcon(grid, "Files", "Browse VPS", "/", "gold", v -> showFiles());
        appIcon(grid, "System", "Server health", "*", "blue", v -> showSystem());
        appIcon(grid, "Docker", "Containers", "#", "violet", v -> showDocker());
        appIcon(grid, "Settings", "SSH login", "@", "muted", v -> showSettings());

        TextView hint = label("Tap an app. Connection details live in Settings.", 13, "muted", false);
        hint.setPadding(0, dp(18), 0, 0);
        body.addView(hint);
    }

    private void showCodex() {
        openApp("Codex", "Dedicated VPS agent");
        TextView intro = label("Prompt Codex on the server. Install it first if the VPS says the command is missing.", 14, "muted", false);
        intro.setPadding(0, 0, 0, dp(8));
        body.addView(intro);

        codexPromptInput = input("Ask Codex", "Inspect the current folder and tell me what should be improved.", false);
        codexPromptInput.setSingleLine(false);
        codexPromptInput.setMinLines(4);
        body.addView(codexPromptInput);

        LinearLayout row = row();
        row.addView(action("Run Codex", v -> runCodexPrompt(), true), weight());
        row.addView(action("Install", v -> installCodex(), false), weight());
        body.addView(row);

        LinearLayout tools = row();
        tools.addView(action("Doctor", v -> runCommand("codex doctor || true", "codex doctor"), false), weight());
        tools.addView(action("Version", v -> runCommand("command -v codex && codex --version || echo 'Codex CLI is not installed.'", "codex --version"), false), weight());
        body.addView(tools);

        terminalPanel("Codex output");
    }

    private void showTerminal() {
        openApp("Terminal", "Direct command runner");
        commandInput = input("Command", "pwd && ls -la", false);
        commandInput.setSingleLine(false);
        commandInput.setMinLines(3);
        body.addView(commandInput);
        body.addView(action("Run Command", v -> runCommand(commandInput.getText().toString(), commandInput.getText().toString()), true));

        String[] labels = {"Uptime", "Disk", "Memory", "Processes"};
        String[] commands = {
                "hostname && uptime",
                "df -h",
                "free -h",
                "ps aux --sort=-%mem | head -15"
        };
        quickGrid(labels, commands);
        terminalPanel("Terminal");
    }

    private void showFiles() {
        openApp("Files", "Folder drawer");
        pathInput = input("Remote folder", prefs.getString("path", "/home/abbas"), false);
        body.addView(pathInput);
        body.addView(action("Open Folder", v -> listFiles(pathInput.getText().toString()), true));

        LinearLayout row = row();
        row.addView(action("Home", v -> listFiles("/home/abbas"), false), weight());
        row.addView(action("Projects", v -> runCommand("find /home/abbas -maxdepth 2 -type d | sort | head -100", "Project folders"), false), weight());
        body.addView(row);
        terminalPanel("Files");
    }

    private void showSystem() {
        openApp("System", "Server status");
        String[] labels = {"Overview", "Storage", "Updates", "Network"};
        String[] commands = {
                "hostname && uptime && free -h",
                "df -h && du -sh ~ 2>/dev/null",
                "sudo -n apt list --upgradable 2>/dev/null || apt list --upgradable 2>/dev/null",
                "ip -br addr && ss -tulpn 2>/dev/null | head -40"
        };
        quickGrid(labels, commands);
        terminalPanel("System output");
    }

    private void showDocker() {
        openApp("Docker", "Containers and logs");
        String[] labels = {"Containers", "Images", "Compose", "Disk"};
        String[] commands = {
                "docker ps --format 'table {{.Names}}\\t{{.Status}}\\t{{.Ports}}' 2>/dev/null || echo 'Docker is not installed or not available.'",
                "docker images 2>/dev/null || echo 'Docker is not installed or not available.'",
                "find /home/abbas -name docker-compose.yml -o -name compose.yml 2>/dev/null | head -50",
                "docker system df 2>/dev/null || echo 'Docker is not installed or not available.'"
        };
        quickGrid(labels, commands);
        terminalPanel("Docker output");
    }

    private void showSettings() {
        openApp("Settings", "SSH connection");
        hostInput = input("Host", prefs.getString("host", "62.171.166.27"), false);
        userInput = input("User", prefs.getString("user", "abbas"), false);
        passwordInput = input("Password", prefs.getString("password", ""), true);
        body.addView(hostInput);
        body.addView(userInput);
        body.addView(passwordInput);

        LinearLayout row = row();
        row.addView(action("Save", v -> saveConnection(true), true), weight());
        row.addView(action("Test", v -> testConnection(), false), weight());
        body.addView(row);

        terminalPanel("Connection test");
    }

    private void openApp(String title, String subtitle) {
        body.removeAllViews();
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(0, 0, 0, dp(12));

        Button back = smallButton("<", v -> showHome());
        top.addView(back, new LinearLayout.LayoutParams(dp(44), dp(44)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(12), 0, 0, 0);
        copy.addView(label(title, 26, "ink", true));
        copy.addView(label(subtitle, 13, "muted", false));
        top.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        body.addView(top);
    }

    private void quickGrid(String[] labels, String[] commands) {
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(2);
        for (int i = 0; i < labels.length; i++) {
            String command = commands[i];
            appTile(grid, labels[i], command, v -> runCommand(command, labels[(int) v.getTag()]));
            grid.getChildAt(grid.getChildCount() - 1).setTag(i);
        }
        body.addView(grid);
    }

    private void terminalPanel(String title) {
        TextView heading = label(title, 15, "ink", true);
        heading.setPadding(0, dp(14), 0, dp(8));
        body.addView(heading);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackground(round(0xFF101815, dp(16), 0));
        terminal = label("", 13, "terminal_text", false);
        terminal.setTypeface(Typeface.MONOSPACE);
        terminal.setPadding(dp(14), dp(14), dp(14), dp(14));
        terminal.setText("Output will appear here.");
        scroll.addView(terminal);
        body.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    }

    private void appIcon(GridLayout grid, String title, String subtitle, String mark, String colorName, View.OnClickListener listener) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setPadding(dp(10), dp(14), dp(10), dp(12));
        tile.setBackground(round(color("panel"), dp(22), color("line")));
        tile.setOnClickListener(listener);

        TextView icon = label(mark, 27, "panel", true);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(round(color(colorName), dp(18), 0));
        tile.addView(icon, new LinearLayout.LayoutParams(dp(64), dp(64)));

        TextView name = label(title, 16, "ink", true);
        name.setGravity(Gravity.CENTER);
        name.setPadding(0, dp(9), 0, 0);
        tile.addView(name);

        TextView sub = label(subtitle, 12, "muted", false);
        sub.setGravity(Gravity.CENTER);
        tile.addView(sub);

        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 0;
        params.height = dp(150);
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        params.setMargins(dp(5), dp(5), dp(5), dp(5));
        grid.addView(tile, params);
    }

    private void appTile(GridLayout grid, String title, String subtitle, View.OnClickListener listener) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setPadding(dp(12), dp(12), dp(12), dp(12));
        tile.setGravity(Gravity.CENTER_VERTICAL);
        tile.setBackground(round(color("panel"), dp(16), color("line")));
        tile.setOnClickListener(listener);
        tile.addView(label(title, 15, "ink", true));
        TextView sub = label(subtitle, 11, "muted", false);
        sub.setMaxLines(2);
        tile.addView(sub);

        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 0;
        params.height = dp(92);
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        params.setMargins(dp(4), dp(4), dp(4), dp(4));
        grid.addView(tile, params);
    }

    private void runCodexPrompt() {
        String prompt = codexPromptInput.getText().toString().trim();
        if (prompt.isEmpty()) {
            append("Enter a Codex prompt first.");
            return;
        }
        String command = "mkdir -p ~/codex-workspace && cd ~/codex-workspace && codex exec --dangerously-bypass-approvals-and-sandbox --ask-for-approval never " + shellQuote(prompt);
        runCommand(command, "codex exec " + shellQuote(prompt));
    }

    private void installCodex() {
        String password = prefs.getString("password", "");
        if (password.isEmpty()) {
            append("Open Settings and save the SSH password first. The installer may need sudo for Node.js.");
            return;
        }
        String sudoPrefix = "printf %s\\\\n " + shellQuote(password) + " | sudo -S -p '' ";
        String command =
                "set -e; " +
                "if ! command -v node >/dev/null 2>&1 || ! command -v npm >/dev/null 2>&1; then " +
                "curl -fsSL https://deb.nodesource.com/setup_22.x | " + sudoPrefix + "bash - && " +
                sudoPrefix + "apt-get install -y nodejs; " +
                "fi; " +
                "npm install -g @openai/codex; " +
                "codex --version";
        runCommand(command, "Install or update Codex CLI");
    }

    private void testConnection() {
        saveConnection(false);
        runCommand("echo connected && hostname && whoami", "Test SSH connection");
    }

    private void runCommand(String command, String display) {
        if (command.trim().isEmpty()) {
            append("No command entered.");
            return;
        }
        saveConnection(false);
        append("$ " + display);
        status.setText("Running...");
        executor.execute(() -> {
            Session session = null;
            ChannelExec channel = null;
            try {
                session = openSession();
                channel = (ChannelExec) session.openChannel("exec");
                channel.setPty(true);
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
                runOnUiThread(() -> status.setText(exit == 0 ? "Done" : "Finished with exit " + exit));
            } catch (Exception ex) {
                append("Error: " + ex.getMessage());
                runOnUiThread(() -> status.setText("Connection error"));
            } finally {
                if (channel != null) channel.disconnect();
                if (session != null) session.disconnect();
            }
        });
    }

    private void listFiles(String path) {
        saveConnection(false);
        prefs.edit().putString("path", path).apply();
        append("sftp> " + path);
        status.setText("Opening folder...");
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
                    builder.append(item.getAttrs().isDir() ? "folder  " : "file    ");
                    builder.append(item.getFilename()).append('\n');
                }
                append(builder.toString().trim());
                runOnUiThread(() -> status.setText("Folder loaded"));
            } catch (Exception ex) {
                append("Error: " + ex.getMessage());
                runOnUiThread(() -> status.setText("SFTP error"));
            } finally {
                if (sftp != null) sftp.disconnect();
                if (session != null) session.disconnect();
            }
        });
    }

    private void saveConnection(boolean notify) {
        String host = hostInput != null ? hostInput.getText().toString().trim() : prefs.getString("host", "62.171.166.27");
        String user = userInput != null ? userInput.getText().toString().trim() : prefs.getString("user", "abbas");
        String password = passwordInput != null ? passwordInput.getText().toString() : prefs.getString("password", "");
        prefs.edit().putString("host", host).putString("user", user).putString("password", password).apply();
        if (notify) {
            Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show();
            status.setText(connectionLine());
        }
    }

    private Session openSession() throws Exception {
        String host = prefs.getString("host", "62.171.166.27").trim();
        String user = prefs.getString("user", "abbas").trim();
        String password = prefs.getString("password", "");
        if (host.isEmpty() || user.isEmpty() || password.isEmpty()) {
            throw new IllegalStateException("Open Settings and save host, user, and password first.");
        }
        JSch jsch = new JSch();
        Session session = jsch.getSession(user, host, 22);
        session.setPassword(password);
        Properties config = new Properties();
        config.put("StrictHostKeyChecking", "no");
        session.setConfig(config);
        session.connect(15000);
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
            if (terminal == null) {
                return;
            }
            String old = terminal.getText().toString();
            terminal.setText(old.equals("Output will appear here.") ? message : old + "\n\n" + message);
        });
    }

    private String connectionLine() {
        return prefs.getString("user", "abbas") + "@" + prefs.getString("host", "62.171.166.27");
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        return row;
    }

    private LinearLayout.LayoutParams weight() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(52), 1);
        params.setMargins(dp(3), dp(5), dp(3), dp(5));
        return params;
    }

    private EditText input(String hint, String value, boolean password) {
        EditText edit = new EditText(this);
        edit.setHint(hint);
        edit.setText(value);
        edit.setSingleLine(true);
        edit.setTextColor(color("ink"));
        edit.setHintTextColor(color("muted"));
        edit.setTextSize(15);
        edit.setPadding(dp(14), dp(10), dp(14), dp(10));
        edit.setImeOptions(EditorInfo.IME_ACTION_DONE);
        edit.setInputType(password ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD : InputType.TYPE_CLASS_TEXT);
        edit.setBackground(round(color("panel"), dp(14), color("line")));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(5), 0, dp(9));
        edit.setLayoutParams(params);
        return edit;
    }

    private Button action(String label, View.OnClickListener listener, boolean primary) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(14);
        button.setTextColor(primary ? Color.WHITE : color("ink"));
        button.setBackground(round(primary ? color("green") : color("panel"), dp(14), primary ? 0 : color("line")));
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(52));
        params.setMargins(0, dp(5), 0, dp(5));
        button.setLayoutParams(params);
        return button;
    }

    private Button smallButton(String label, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(20);
        button.setTextColor(color("ink"));
        button.setBackground(round(color("panel"), dp(14), color("line")));
        button.setOnClickListener(listener);
        return button;
    }

    private TextView label(String value, int sp, String colorName, boolean bold) {
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

    private GradientDrawable round(int fill, int radius, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(radius);
        if (stroke != 0) {
            drawable.setStroke(dp(1), stroke);
        }
        return drawable;
    }

    private int color(String name) {
        int id = getResources().getIdentifier(name, "color", getPackageName());
        return getResources().getColor(id, getTheme());
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
