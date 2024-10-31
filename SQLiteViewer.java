package viewer;

import org.sqlite.SQLiteDataSource;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class SQLiteViewer extends JFrame {
    private Storage storage;

    public SQLiteViewer() {
        storage = new Storage();
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(700, 900);
        setLayout(new GridBagLayout());
        setResizable(false);
        setLocationRelativeTo(null);
        setTitle("SQLite Viewer");

        JPanel panel = new JPanel(new GridLayout(4,1));

        panel.add(storage.getOpenDatabasePanel());
        panel.add(storage.getTableNamesSelect());
        panel.add(storage.getQueryPanel());
        panel.add(storage.getExecuteButtonPanel());

        panel.setMaximumSize(new Dimension(500, 500));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0;
        gbc.gridy = 0;
        add(panel, gbc);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.ipady = 20;
        gbc.gridx = 0;
        gbc.gridy = 1;
        add(storage.getTablePanel(), gbc);
        setVisible(true);
    }
}

class Storage {
    private OpenDatabasePanel openDatabasePanel;
    private TableSelector tableNamesSelect;
    private QueryPanel queryPanel;
    private ExecuteButtonPanel executeButtonPanel;
    private TablePanel tablePanel;

    public OpenDatabasePanel getOpenDatabasePanel() {
        if (openDatabasePanel == null) {
            openDatabasePanel = new OpenDatabasePanel();
        }
        return openDatabasePanel;
    }

    public TableSelector getTableNamesSelect() {
        if (tableNamesSelect == null) {
            tableNamesSelect = new TableSelector();
        }
        return tableNamesSelect;
    }

    public QueryPanel getQueryPanel() {
        if (queryPanel == null) {
            queryPanel = new QueryPanel();
        }
        return queryPanel;
    }

    public ExecuteButtonPanel getExecuteButtonPanel() {
        if (executeButtonPanel == null) {
            executeButtonPanel = new ExecuteButtonPanel();
        }
        return executeButtonPanel;
    }

    public TablePanel getTablePanel() {
        if (tablePanel == null) {
            tablePanel = new TablePanel();
        }
        return tablePanel;
    }

    class OpenDatabasePanel extends LabeledJPanel{
        private JTextField nameField;
        private JButton openButton;

        OpenDatabasePanel() {
            super("Database: ");

            nameField = new JTextField();
            nameField.setToolTipText("Enter a database file name please..");
            nameField.setName("FileNameTextField");
//            nameField.setPreferredSize(new Dimension(200, 30));

            openButton = new JButton("OpenFileButton");
            openButton.setText("Open");
            openButton.setName("OpenFileButton");
            openButton.addActionListener(new DoOpenDb());

            setLayout(new GridLayout(1, 3));

            add(label);
            add(nameField);
            add(openButton);
        }

        public String value() {
            return nameField.getText();
        }

        public SQLiteDataSource getDataSource() {
            String dbName = value();
            if (dbName.isEmpty()) return null;
            if (!Files.exists(Path.of(dbName))) return null;
            String url = "jdbc:sqlite:" + dbName;
            SQLiteDataSource dataSource = new SQLiteDataSource();
            dataSource.setUrl(url);
            return dataSource;
        }

        class DoOpenDb implements ActionListener {

            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                getQueryPanel().disableControl();
                getTableNamesSelect().disableControl();
                getExecuteButtonPanel().disableControl();

                SQLiteDataSource dataSource = getDataSource();

                try (Connection con = dataSource.getConnection()) {
                    if (con.isValid(5)) {
                        System.out.println("Connection is valid.");
                        getQueryPanel().enableControl();
                        getTableNamesSelect().enableControl();
                        getExecuteButtonPanel().enableControl();
                        String sql = "SELECT name FROM sqlite_schema WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name";
                        try (ResultSet tables = con.prepareStatement(sql).executeQuery();) {
                            ArrayList<String> options = new ArrayList<>();
                            while (tables.next()) {
                                options.add(tables.getString("name"));
                                System.out.println(tables.getString("name"));
                            }
                            getTableNamesSelect().setOptions(options);
                        }
                    }
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(new Frame(), "File doesn't exist!");
                    e.printStackTrace();
                }
            }
        }
    }

    class TableSelector extends LabeledJPanel {
        private JComboBox tablesList;

        TableSelector() {
            super("Table: ");

            tablesList = new JComboBox();
            tablesList.setName("TablesComboBox");
            tablesList.addItemListener(new ItemSelected());

            add(label);
            add(tablesList);
        }

        public void enableControl() {
            tablesList.setEnabled(true);
        }
        public void disableControl() {
            tablesList.removeAllItems();
            tablesList.setEnabled(false);
        }

        public void setOptions(List<String> options) {
            options.stream().forEach((item)->{
                tablesList.addItem(item);
            });
        }

        class ItemSelected implements ItemListener {
            @Override
            public void itemStateChanged(ItemEvent event) {
                if (event.getStateChange() == ItemEvent.SELECTED) {
                    Object item = event.getItem();
                    getQueryPanel().generateQuery(item.toString());
                }
            }
        }
    }

    class QueryPanel extends LabeledJPanel {
        private JTextArea queryField;

        QueryPanel() {
            super("Query: ");

            queryField = new JTextArea();
            queryField.setName("QueryTextArea");

            add(label);
            add(queryField);
            disableControl();
        }

        public void enableControl() {
            queryField.setEnabled(true);
        }
        public void disableControl() {
            queryField.setText("");
            queryField.setEnabled(false);
        }

        public void generateQuery(String tableName) {
            String query = "SELECT * FROM %s;".formatted(tableName);
            queryField.setText(query);
        }

        public String getQuery() {
            return queryField.getText();
        }

    }

    class ExecuteButtonPanel extends JPanel {
        private JButton executeButton;

        ExecuteButtonPanel() {
            executeButton = new JButton();
            executeButton.setText("Execute");
            executeButton.setName("ExecuteQueryButton");
            executeButton.addActionListener(new DoExecuteQuery());

            add(executeButton);
            disableControl();
        }

        public void enableControl() {
            executeButton.setEnabled(true);
        }
        public void disableControl() {
            executeButton.setEnabled(false);
        }

        class DoExecuteQuery implements ActionListener {

            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                SQLiteDataSource dataSource = getOpenDatabasePanel().getDataSource();

                try (Connection con = dataSource.getConnection()) {
                    if (con.isValid(5)) {
                        String sql = getQueryPanel().getQuery();
                        try (ResultSet data = con.prepareStatement(sql).executeQuery();) {
                            Integer columnCount = data.getMetaData().getColumnCount();
                            ArrayList<String> columns = new ArrayList<>();
                            for (int colI = 1; colI <= columnCount; colI++) {
                                columns.add(data.getMetaData().getColumnName(colI));
                            }
                            ArrayList<ArrayList<String>> dataList = new ArrayList<>();
                            while (data.next()) {
                                ArrayList<String> row = new ArrayList<>();
                                for (int colI = 1; colI <= columnCount; colI++) {
                                    row.add(data.getString(colI));
                                }
                                dataList.add(row);
                            }
                            getTablePanel().setData(dataList, columns);
                        }

                    }
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(new Frame(), e.getMessage());
                    e.printStackTrace();
                }

            }
        }
    }

    class LabeledJPanel extends JPanel {

        protected JLabel label;

        LabeledJPanel(String text) {
            label = new JLabel(text);
            label.setText(text);

            setLayout(new GridLayout(1, 2));
        }
    }

    class TablePanel extends JPanel {
        private JTable table;

        TablePanel() {
            table = new JTable();
            table.setName("Table");

            add(new JScrollPane(table));
        }

        public void setData(ArrayList<ArrayList<String>> data, ArrayList<String> columns) {
            TableModel model = new AbstractTableModel() {
                @Override
                public int getRowCount() {
                    return data.size();
                }

                @Override
                public int getColumnCount() {
                    return columns.size();
                }

                @Override
                public Object getValueAt(int i, int j) {
                    return data.get(i).get(j);
                }

                @Override
                public String getColumnName(int column) {
                    return columns.get(column);
                }
            };

            table.setModel(model);
        }
    }


}