package org.jabref.logic.openoffice.oocsltext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jabref.model.entry.BibEntry;

import com.sun.star.container.XNameAccess;
import com.sun.star.container.XNamed;
import com.sun.star.lang.XMultiServiceFactory;
import com.sun.star.text.XReferenceMarksSupplier;
import com.sun.star.text.XTextDocument;
import com.sun.star.uno.UnoRuntime;

public class CSLReferenceMarkManager {
    private final HashMap<String, CSLReferenceMark> marksByName;
    private final ArrayList<CSLReferenceMark> marksByID;
    private final IdentityHashMap<CSLReferenceMark, Integer> idsByMark;
    private final XTextDocument document;
    private final XMultiServiceFactory factory;
    private final HashMap<String, Integer> citationKeyToNumber;
    private int highestCitationNumber = 0;
    private final Map<String, Integer> citationOrder = new HashMap<>();

    public CSLReferenceMarkManager(XTextDocument document) {
        this.document = document;
        this.factory = UnoRuntime.queryInterface(XMultiServiceFactory.class, document);
        this.marksByName = new HashMap<>();
        this.marksByID = new ArrayList<>();
        this.idsByMark = new IdentityHashMap<>();
        this.citationKeyToNumber = new HashMap<>();
    }

    public void readExistingMarks() throws Exception {
        XReferenceMarksSupplier supplier = UnoRuntime.queryInterface(XReferenceMarksSupplier.class, document);
        XNameAccess marks = supplier.getReferenceMarks();

        citationOrder.clear();
        int citationCounter = 0;

        for (String name : marks.getElementNames()) {
            String citationKey = extractCitationKey(name);
            if (!citationKey.isEmpty()) {
                citationOrder.putIfAbsent(citationKey, ++citationCounter);

                XNamed named = UnoRuntime.queryInterface(XNamed.class, marks.getByName(name));
                CSLReferenceMark mark = new CSLReferenceMark(named, name);
                addMark(mark);
            }
        }
    }

    /**
     * Extracts the citation key from a reference mark name.
     *
     * @param name The name of the reference mark
     * @return The extracted citation key, or an empty string if no key could be extracted
     */
    private String extractCitationKey(String name) {
        // Check if the name starts with one of the known prefixes
        for (String prefix : CSLCitationOOAdapter.PREFIXES) {
            if (name.startsWith(prefix)) {
                // Remove the prefix
                String withoutPrefix = name.substring(prefix.length());

                // Split the remaining string by space
                String[] parts = withoutPrefix.split("\\s+");

                if (parts.length > 0) {
                    // The first part should be the citation key
                    String key = parts[0];

                    // Remove any non-alphanumeric characters from the start and end
                    key = key.replaceAll("^[^a-zA-Z0-9]+|[^a-zA-Z0-9]+$", "");

                    // If we have a non-empty key, return it
                    if (!key.isEmpty()) {
                        return key;
                    }
                }

                // If we couldn't extract a key after removing the prefix,
                // no need to check other prefixes
                break;
            }
        }

        // If no key could be extracted, return an empty string
        return "";
    }

    private void updateCitationInfo(String name) {
        Pattern pattern = Pattern.compile("JABREF_(.+) RND(\\d+)"); // Format: JABREF_{citationKey} RND{citationNumber}
        Matcher matcher = pattern.matcher(name);
        if (matcher.find()) {
            String citationKey = matcher.group(1);
            int citationNumber = Integer.parseInt(matcher.group(2));
            citationKeyToNumber.put(citationKey, citationNumber);
            highestCitationNumber = Math.max(highestCitationNumber, citationNumber);
        }
    }

    public void addMark(CSLReferenceMark mark) {
        marksByName.put(mark.getName(), mark);
        idsByMark.put(mark, marksByID.size());
        marksByID.add(mark);
        updateCitationInfo(mark.getName());
    }

    public int getCitationNumber(String citationKey) {
        return citationKeyToNumber.computeIfAbsent(citationKey, k -> {
            highestCitationNumber++;
            return highestCitationNumber;
        });
    }

    public CSLReferenceMark createReferenceMark(BibEntry entry) throws Exception {
        String citationKey = entry.getCitationKey().orElse("");
        int citationNumber = getCitationNumber(citationKey);

        String name = CSLCitationOOAdapter.PREFIXES[0] + citationKey + " RND" + citationNumber;
        Object mark = factory.createInstance("com.sun.star.text.ReferenceMark");
        XNamed named = UnoRuntime.queryInterface(XNamed.class, mark);
        named.setName(name);

        CSLReferenceMark referenceMark = new CSLReferenceMark(named, name);
        addMark(referenceMark);

        return referenceMark;
    }

    public boolean hasCitationForKey(String citationKey) {
        return citationKeyToNumber.containsKey(citationKey);
    }
}
