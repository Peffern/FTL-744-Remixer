package com.peffern.ftlremix;

import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JEditorPane;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.HyperlinkEvent;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;

// Standalone Java program that builds the 744 Remix mod
// Contains a link to the album and a file chooser.
// After selecting the album, will bundle a Slipstream mod containing
// the music and the necessary config changes to play it in the game.
public class Remixer
{
    /* Swing GUI stuff */
    // UI window
    private final JFrame frame;
    // file chooser text field, for reading the album location
    private final JTextField zipInput;
    // progress bar for showing copy progress
    private final JProgressBar progressBar;

    // Text to show in the GUI
    private static final String HELP_TEXT = "This program will generate an FTL mod that replaces the in-game soundtrack with remixed versions by 744 Music. This program does not contain the remixed tracks, since they belong to 744 Music. You can get the album <a href=\"https://744music.bandcamp.com/album/ftl-remix-project\">here</a> (make sure to get the .ogg version), then select the file below and click Generate Mod. This will produce a .ftl mod file that is compatible with Slipstream. The mod will be generated in the same folder as the album.";

    // # of files expected to be inside the album, for updating progress bar
    private static final int ALBUM_COUNT = 27;

    // Contents of sounds.xml.append, to be written out to the mod archive (loaded from internal resource).
    private static final Document APPEND_SOUNDS_XML;
    // Contents of metadata.xml, to be written out to the mod archive (loaded from internal resource)
    private static final Document METADATA_XML;
    // XML Output transformer, to be used to write the XML files

    private static final Transformer XML_WRITER;

    // load and preprocess XML data from resources
    static
    {
        try
        {
            DocumentBuilder db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            try (InputStream is = Remixer.class.getResourceAsStream("/appendSounds.xml"))
            {
                APPEND_SOUNDS_XML = db.parse(is);
                // We have to append the xmlns attribute so that it parses as valid xml, but Slipstream doesn't actually want it, so strip it off.
                // It wasn't a real xsd link anyway; it was just a link to the thread for Slipstream itself.
                APPEND_SOUNDS_XML.getDocumentElement().removeAttribute("xmlns:mod");
            }
            try (InputStream is = Remixer.class.getResourceAsStream("/metadata.xml"))
            {
                METADATA_XML = db.parse(is);
            }

            XML_WRITER = TransformerFactory.newInstance().newTransformer();
            // We don't want to write the xml declaration, since Slipstream expects plain xml only.
            XML_WRITER.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        }
        catch (Exception ex)
        {
            // If we can't load the internal XML, show an error dialog and exit
            JOptionPane.showMessageDialog(null, "Error loading mod resources");
            System.exit(1);
            // throw unchecked exception here even though it will never be reached, so that the static{} block typechecks
            throw new Error();
        }
    }
    // constructor to do UI init
    private Remixer()
    {
        // create window and content pane
        frame = new JFrame("FTL 744 Remixer");
        frame.setSize(new Dimension(400, 400));
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        // We use JEditorPane instead of JLabel here so that the link is clickable
        JEditorPane editorPane = new JEditorPane("text/html", HELP_TEXT);
        editorPane.setEditable(false);
        editorPane.setOpaque(false);
        editorPane.addHyperlinkListener((HyperlinkEvent e) ->
        {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED)
            {
                // on link clicked, try to open the link in browser
                try
                {
                    Desktop.getDesktop().browse(e.getURL().toURI());
                }
                catch (Exception ex)
                {
                    // If we can't open the link, show an error message
                    JOptionPane.showMessageDialog(frame, ex.getMessage());
                }
            }
        });

        panel.add(editorPane);

        // create file chooser components
        zipInput = new JTextField(15);
        JButton chooseZip = new JButton("...");

        JFileChooser chooser = new JFileChooser();
        FileNameExtensionFilter filter = new FileNameExtensionFilter("Zip Archives","zip");
        chooser.setFileFilter(filter);

        chooseZip.addActionListener((ActionEvent e) ->
        {
            if (chooser.showOpenDialog(chooseZip) == JFileChooser.APPROVE_OPTION) {
                zipInput.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });

        JPanel center = new JPanel(new FlowLayout());

        center.add(new JLabel("Select album zip file:"));
        center.add(zipInput);
        center.add(chooseZip);

        panel.add(center);

        // create button to actually output the mod, and progress bar
        JButton convert = new JButton("Generate Mod");

        // run conversion in separate thread in order to not block the UI thread
        convert.addActionListener((ActionEvent e) -> new Thread(this::convert).start());
        convert.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(convert);

        progressBar = new JProgressBar(SwingConstants.HORIZONTAL, 0, ALBUM_COUNT);
        progressBar.setMaximumSize(new Dimension(200, 50));
        progressBar.setStringPainted(true);
        progressBar.setString("Copying tracks");
        progressBar.setVisible(false);
        panel.add(Box.createRigidArea(new Dimension(0, 40)));

        panel.add(progressBar);
        panel.add(Box.createRigidArea(new Dimension(0, 60)));

        frame.setContentPane(panel);
    }
    // non-static entry point, so we have something to call from main()
    void start()
    {
        frame.setVisible(true);
    }

    // actually read the album and build the mod
    void convert()
    {
        try
        {
            // make sure we actually found the album
            String path = zipInput.getText();
            if (path == null || !path.endsWith(".zip"))
            {
                throw new Exception("File " + path + " is not a zip archive");
            }

            File file = new File(path);
            File parent = file.getParentFile();
            // create the output mod next to the album zip
            File output = new File(parent, "744remix.ftl");

            // File RW buffer
            byte[] buf = new byte[4096];

            SwingUtilities.invokeLater(() -> progressBar.setVisible(true));
            try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(output)); ZipInputStream zis = new ZipInputStream(new FileInputStream(file)))
            {
                // copy tracks from the album to the mod
                ZipEntry ze;
                boolean seenOgg = false;
                int count = 0;
                while ((ze = zis.getNextEntry()) != null)
                {
                    int newCount = count++;
                    SwingUtilities.invokeLater(() -> progressBar.setValue(newCount));
                    String name = ze.getName();
                    // skip cover art, etc.
                    if(name.endsWith(".ogg")) {
                        seenOgg = true;
                        // put tracks in audio/music and replace characters in the title that FTL doesn't like
                        zos.putNextEntry(new ZipEntry("audio/music/" + name.replaceAll("[^\\w.]", "_")));
                        int c;
                        // write track
                        while ((c = zis.read(buf, 0, buf.length)) >= 0)
                        {
                            zos.write(buf, 0, c);
                        }
                        zos.closeEntry();
                    }
                    zis.closeEntry();
                }
                // spot check in case someone has the wrong version of the album
                if (!seenOgg)
                {
                    throw new Error("No .ogg files found in file " + path);
                }

                SwingUtilities.invokeLater(() -> progressBar.setString("Building mod"));

                // write the actual mod data. track names are hardcoded
                zos.putNextEntry(new ZipEntry("data/sounds.xml.append"));
                XML_WRITER.transform(new DOMSource(APPEND_SOUNDS_XML), new StreamResult(zos));
                zos.closeEntry();

                // write mod metadata for Slipstream
                zos.putNextEntry(new ZipEntry("mod-appendix/metadata.xml"));
                XML_WRITER.transform(new DOMSource(METADATA_XML), new StreamResult(zos));
                zos.closeEntry();

                SwingUtilities.invokeLater(() ->
                {
                    progressBar.setValue(ALBUM_COUNT);
                    progressBar.setString("Done");
                });
            }
        }
        catch (Exception ex)
        {
            // If we fail at any point, show an error message and stop
            JOptionPane.showMessageDialog(frame, ex.getMessage());
        }
    }

    // main method so that the jar file will be executable
    public static void main(String[] args)
    {
        new Remixer().start();
    }
}