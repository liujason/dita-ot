/*
 * This file is part of the DITA Open Toolkit project hosted on
 * Sourceforge.net. See the accompanying license.txt file for
 * applicable licenses.
 */

/*
 * (c) Copyright IBM Corp. 2005, 2006 All Rights Reserved.
 */
package org.dita.dost.reader;

import static org.dita.dost.util.Constants.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import org.dita.dost.index.IndexTerm;
import org.dita.dost.index.IndexTermCollection;
import org.dita.dost.index.IndexTermTarget;
import org.dita.dost.log.MessageUtils;
import org.dita.dost.util.StringUtils;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

/**
 * This class extends SAX's DefaultHandler, used for parse index term from dita
 * files.
 * 
 * @version 1.0 2005-04-30
 * 
 * @author Wu, Zhi Qiang
 */
public final class IndexTermReader extends AbstractXMLReader {
    /** The target file under parsing */
    private String targetFile = null;

    /** The title of the topic under parsing */
    private String title = null;

    /** The title of the main topic */
    private String defaultTitle = null;

    /** Whether or not current element under parsing is a title element */
    private boolean inTitleElement = false;

    /** Whether or not current element under parsing is <index-sort-as> */
    private boolean insideSortingAs = false;

    /** Stack used to store index term */
    private final Stack<IndexTerm> termStack;

    /** Stack used to store topic id */
    private final Stack<String> topicIdStack;

    /** List used to store all the specialized index terms */
    private final List<String> indexTermSpecList;

    /** List used to store all the specialized index-see */
    private final List<String> indexSeeSpecList;

    /** List used to store all the specialized index-see-also */
    private final List<String> indexSeeAlsoSpecList;

    /** List used to store all the specialized index-sort-as */
    private final List<String> indexSortAsSpecList;

    /** List used to store all the specialized topics */
    private final List<String> topicSpecList;

    /** List used to store all specialized titles */
    private final List<String> titleSpecList;

    /** List used to store all the indexterm found in this topic file */
    private final List<IndexTerm> indexTermList;

    /** Map used to store the title info accessed by its topic id*/
    private final Map<String, String> titleMap;

    /** Stack for "@processing-role" value */
    private final Stack<String> processRoleStack;

    /** Depth inside a "@processing-role" parent */
    private int processRoleLevel = 0;

    //Added by William on 2010-04-26 for ref:2990783 start
    private IndexTermCollection result;
    //Added by William on 2010-04-26 for ref:2990783 end

    //Added by William on 2010-04-26 for ref:2990783 start
    public IndexTermReader(final IndexTermCollection result) {
        this();
        this.result = result;
    }
    //Added by William on 2010-04-26 for ref:2990783 end

    /**
     * Constructor.
     * 
     * @deprecated use {@link #IndexTermReader(IndexTermCollection)} instead
     */
    @Deprecated
    public IndexTermReader() {
        termStack = new Stack<IndexTerm>();
        topicIdStack = new Stack<String>();
        indexTermSpecList = new ArrayList<String>(INT_16);
        indexSeeSpecList = new ArrayList<String>(INT_16);
        indexSeeAlsoSpecList = new ArrayList<String>(INT_16);
        indexSortAsSpecList = new ArrayList<String>(INT_16);
        topicSpecList = new ArrayList<String>(INT_16);
        titleSpecList = new ArrayList<String>(INT_16);
        indexTermList = new ArrayList<IndexTerm>(INT_256);
        titleMap = new HashMap<String, String>(INT_256);
        processRoleStack = new Stack<String>();
        processRoleLevel = 0;
        if (result == null) {
            result = IndexTermCollection.getInstantce();
        }
    }

    /**
     * Reset the reader.
     */
    public void reset() {
        targetFile = null;
        title = null;
        defaultTitle = null;
        inTitleElement = false;
        termStack.clear();
        topicIdStack.clear();
        indexTermSpecList.clear();
        indexSeeSpecList.clear();
        indexSeeAlsoSpecList.clear();
        indexSortAsSpecList.clear();
        topicSpecList.clear();
        indexTermList.clear();
        processRoleStack.clear();
        processRoleLevel = 0;
        titleMap.clear();
    }

    @Override
    public void characters(final char[] ch, final int start, final int length)
            throws SAXException {
        final StringBuilder tempBuf = new StringBuilder(length);
        tempBuf.append(ch, start, length);
        normalizeAndCollapseWhitespace(tempBuf);
        String temp = tempBuf.toString();

        /*
         * For title info
         */
        if (processRoleStack.isEmpty() ||
                !ATTR_PROCESSING_ROLE_VALUE_RESOURCE_ONLY.equalsIgnoreCase(processRoleStack.peek())) {
            if (!insideSortingAs && !termStack.empty()) {
                final IndexTerm indexTerm = termStack.peek();
                temp = StringUtils.escapeXML(temp);
                temp = trimSpaceAtStart(temp, indexTerm.getTermName());
                indexTerm.setTermName(StringUtils.setOrAppend(indexTerm.getTermName(), temp, false));
            } else if (insideSortingAs && temp.length() > 0) {
                final IndexTerm indexTerm = termStack.peek();
                temp = StringUtils.escapeXML(temp);
                temp = trimSpaceAtStart(temp, indexTerm.getTermKey());
                indexTerm.setTermKey(StringUtils.setOrAppend(indexTerm.getTermKey(), temp, false));
            } else if (inTitleElement) {
                temp = StringUtils.escapeXML(temp);
                temp = trimSpaceAtStart(temp, title);
                //Always append space if: <title>abc<ph/>df</title>
                //Updated with SF 2010062 - should only add space if one is in source
                title = StringUtils.setOrAppend(title, temp, false);
            }
        }
    }

    @Override
    public void endDocument() throws SAXException {
        final int size = indexTermList.size();
        updateIndexTermTargetName();
        for(int i=0; i<size; i++){
            final IndexTerm indexterm = indexTermList.get(i);
            //IndexTermCollection.getInstantce().addTerm(indexterm);
            //Added by William on 2010-04-26 for ref:2990783 start
            result.addTerm(indexterm);
            //Added by William on 2010-04-26 for ref:2990783 end
        }
    }

    @Override
    public void endElement(final String uri, final String localName, final String qName)
            throws SAXException {

        //Skip the topic if @processing-role="resource-only"
        if (processRoleLevel > 0) {
            String role = processRoleStack.peek();
            if (processRoleLevel == processRoleStack.size()) {
                role = processRoleStack.pop();
            }
            processRoleLevel--;
            if (ATTR_PROCESSING_ROLE_VALUE_RESOURCE_ONLY
                    .equalsIgnoreCase(role)) {
                return;
            }
        }

        // Check to see if the indexterm element or a specialized version is
        // in the list.
        if (indexTermSpecList.contains(localName)) {
            final IndexTerm term = termStack.pop();
            //SF Bug 2010062: Also set to *** when the term is only white-space.
            if (term.getTermName() == null || term.getTermName().trim().equals("")){
                if(term.getEndAttribute() != null && !term.hasSubTerms()){
                    return;
                } else{
                    term.setTermName("***");
                    logger.logWarn(MessageUtils.getMessage("DOTJ014W").toString());
                }
            }

            if (term.getTermKey() == null) {
                term.setTermKey(term.getTermName());
            }

            //if this term is the leaf term
            //leaf means the current indexterm element doesn't contains any subterms
            //or only has "index-see" or "index-see-also" subterms.
            if (term.isLeaf()){
                //generate a target which points to current topic and
                //assign it to current term.
                final IndexTermTarget target = genTarget();
                term.addTarget(target);
            }

            if (termStack.empty()) {
                //most parent indexterm
                indexTermList.add(term);
            } else {
                //Assign parent indexterm to
                final IndexTerm parentTerm = termStack.peek();
                parentTerm.addSubTerm(term);
            }
        }

        // Check to see if the index-see or index-see-also or a specialized
        // version is in the list.
        if (indexSeeSpecList.contains(localName)
                || indexSeeAlsoSpecList.contains(localName)) {
            final IndexTerm term = termStack.pop();
            final IndexTerm parentTerm = termStack.peek();
            if (term.getTermKey() == null) {
                term.setTermKey(term.getTermFullName());
            }
            //term.addTargets(parentTerm.getTargetList());
            term.addTarget(genTarget()); //assign current topic as the target of index-see or index-see-also term
            parentTerm.addSubTerm(term);
        }

        /*
         * For title info
         */
        if (titleSpecList.contains(localName)) {
            inTitleElement = false;
            if(!titleMap.containsKey(topicIdStack.peek())){
                //If this is the first topic title
                if(titleMap.size() == 0) {
                    defaultTitle = title;
                }
                titleMap.put(topicIdStack.peek(), title);
            }
        }

        // For <index-sort-as>
        if (indexSortAsSpecList.contains(localName)) {
            insideSortingAs = false;
        }

        // For <topic>
        if (topicSpecList.contains(localName)){
            topicIdStack.pop();
        }
    }

    /**
     * This method is used to create a target which refers to current topic.
     * @return instance of IndexTermTarget created
     */
    private IndexTermTarget genTarget() {
        final IndexTermTarget target = new IndexTermTarget();
        String fragment = null;

        if(topicIdStack.peek() == null){
            fragment = null;
        }else{
            fragment = topicIdStack.peek();
        }

        if (title != null) {
            target.setTargetName(title);
        } else {
            target.setTargetName(targetFile);
        }
        if(fragment != null) {
            target.setTargetURI(targetFile + SHARP + fragment);
        } else {
            target.setTargetURI(targetFile);
        }
        return target;
    }

    @Override
    public void startElement(final String uri, final String localName, final String qName,
            final Attributes attributes) throws SAXException {

        //Skip the topic if @processing-role="resource-only"
        final String attrValue = attributes
                .getValue(ATTRIBUTE_NAME_PROCESSING_ROLE);
        if (attrValue != null) {
            processRoleStack.push(attrValue);
            processRoleLevel++;
            if (ATTR_PROCESSING_ROLE_VALUE_RESOURCE_ONLY
                    .equals(attrValue)) {
                return;
            }
        } else if (processRoleLevel > 0) {
            processRoleLevel++;
            if (ATTR_PROCESSING_ROLE_VALUE_RESOURCE_ONLY
                    .equals(processRoleStack.peek())) {
                return;
            }
        }

        final String classAttr = attributes.getValue(ATTRIBUTE_NAME_CLASS);

        handleSpecialization(localName, classAttr);
        parseTopic(localName, attributes.getValue(ATTRIBUTE_NAME_ID));
        //change parseIndexTerm(localName) to parseIndexTerm(localName,attributes)
        parseIndexTerm(localName,attributes);
        parseIndexSee(localName);
        parseIndexSeeAlso(localName);

        if (IndexTerm.getTermLocale() == null) {
            final String xmlLang = attributes
                    .getValue(ATTRIBUTE_NAME_XML_LANG);

            if (xmlLang != null) {
                IndexTerm.setTermLocale(StringUtils.getLocale(xmlLang));
            }
        }

        /*
         * For title info
         */
        if (titleSpecList.contains(localName)
                && !titleMap.containsKey(topicIdStack.peek())) {
            inTitleElement = true;
            title = null;
        }

        // For <index-sort-as>
        if (indexSortAsSpecList.contains(localName)) {
            insideSortingAs = true;
        }
    }

    private void parseTopic(final String localName, final String id){
        if (topicSpecList.contains(localName)){
            topicIdStack.push(id);
        }
    }

    private void parseIndexSeeAlso(final String localName) {
        // check to see it the index-see-also element or a specialized version
        // is in the list.
        if (indexSeeAlsoSpecList.contains(localName)) {
            final IndexTerm indexTerm = new IndexTerm();
            IndexTerm parentTerm = null;
            if(!termStack.isEmpty()){
                parentTerm = termStack.peek();
                if(parentTerm.hasSubTerms()){
                    parentTerm.updateSubTerm();
                }
            }
            indexTerm.setTermPrefix(IndexTerm_Prefix_See_Also);
            termStack.push(indexTerm);
        }
    }

    private void parseIndexSee(final String localName) {
        // check to see it the index-see element or a specialized version is
        // in the list.
        if (indexSeeSpecList.contains(localName)) {
            final IndexTerm indexTerm = new IndexTerm();
            IndexTerm parentTerm = null;

            indexTerm.setTermPrefix(IndexTerm_Prefix_See);

            if(!termStack.isEmpty()){
                parentTerm = termStack.peek();
                if(parentTerm.hasSubTerms()){
                    parentTerm.updateSubTerm();
                    indexTerm.setTermPrefix(IndexTerm_Prefix_See_Also);
                }
            }
            termStack.push(indexTerm);
        }
    }

    private void parseIndexTerm(final String localName, final Attributes attributes) {
        // check to see it the indexterm element or a specialized version is
        // in the list.
        if (indexTermSpecList.contains(localName)) {
            final IndexTerm indexTerm = new IndexTerm();
            indexTerm.setStartAttribute(attributes.getValue(ATTRIBUTE_NAME_END));
            indexTerm.setEndAttribute(attributes.getValue(ATTRIBUTE_NAME_END));

            IndexTerm parentTerm = null;
            if(!termStack.isEmpty()){
                parentTerm = termStack.peek();
                if(parentTerm.hasSubTerms()){
                    parentTerm.updateSubTerm();
                }
            }

            termStack.push(indexTerm);
        }
    }

    /**
     * Note: <index-see-also> should be handled before <index-see>.
     * 
     * @param localName
     * @param classAttr
     */
    private void handleSpecialization(final String localName, final String classAttr) {
        if (classAttr == null) {
            return;
        } else if (TOPIC_INDEXTERM.matches(classAttr)) {
            // add the element name to the indexterm specialization element
            // list if it does not already exist in that list.
            if (!indexTermSpecList.contains(localName)) {
                indexTermSpecList.add(localName);
            }
        } else if (INDEXING_D_INDEX_SEE_ALSO.matches(classAttr)) {
            // add the element name to the index-see-also specialization element
            // list if it does not already exist in that list.
            if (!indexSeeAlsoSpecList.contains(localName)) {
                indexSeeAlsoSpecList.add(localName);
            }
        } else if (INDEXING_D_INDEX_SEE.matches(classAttr)) {
            // add the element name to the index-see specialization element
            // list if it does not already exist in that list.
            if (!indexSeeSpecList.contains(localName)) {
                indexSeeSpecList.add(localName);
            }
        } else if (INDEXING_D_INDEX_SORT_AS.matches(classAttr)) {
            // add the element name to the index-sort-as specialization element
            // list if it does not already exist in that list.
            if (!indexSortAsSpecList.contains(localName)) {
                indexSortAsSpecList.add(localName);
            }
        } else if (TOPIC_TOPIC.matches(classAttr)) {
            //add the element name to the topic specialization element
            // list if it does not already exist in that list.
            if (!topicSpecList.contains(localName)) {
                topicSpecList.add(localName);
            }
        } else if (TOPIC_TITLE.matches(classAttr)) {
            //add the element name to the title specailization element list
            // if it does not exist in that list.
            if (!titleSpecList.contains(localName)){
                titleSpecList.add(localName);
            }
        }
    }

    /**
     * Set the current parsing file.
     * @param target The parsingFile to set.
     */
    public void setTargetFile(final String target) {
        this.targetFile = target;
    }

    /**
     * Update the target name of constructed IndexTerm recursively
     *
     */
    private void updateIndexTermTargetName(){
        final int size = indexTermList.size();
        if(defaultTitle == null){
            defaultTitle = targetFile;
        }
        for(int i=0; i<size; i++){
            final IndexTerm indexterm = indexTermList.get(i);
            updateIndexTermTargetName(indexterm);
        }
    }

    /**
     * Update the target name of each IndexTerm, recursively.
     * @param indexterm
     */
    private void updateIndexTermTargetName(final IndexTerm indexterm){
        final int targetSize = indexterm.getTargetList().size();
        final int subtermSize = indexterm.getSubTerms().size();

        for(int i=0; i<targetSize; i++){
            final IndexTermTarget target = indexterm.getTargetList().get(i);
            final String uri = target.getTargetURI();
            final int indexOfSharp = uri.lastIndexOf(SHARP);
            final String fragment = (indexOfSharp == -1 || uri.endsWith(SHARP))?
                    null:
                        uri.substring(indexOfSharp+1);
            if(fragment != null && titleMap.containsKey(fragment)){
                target.setTargetName(titleMap.get(fragment));
            }else{
                target.setTargetName(defaultTitle);
            }
        }

        for(int i=0; i<subtermSize; i++){
            final IndexTerm subterm = indexterm.getSubTerms().get(i);
            updateIndexTermTargetName(subterm);
        }
    }

    /** Whitespace normalization state. */
    private enum WhiteSpaceState { WORD, SPACE };

    /**
     * Normalize and collapse whitespaces from string buffer.
     * 
     * @param strBuffer The string buffer.
     */
    private void normalizeAndCollapseWhitespace(final StringBuilder strBuffer){
        WhiteSpaceState currentState = WhiteSpaceState.WORD;
        for (int i = strBuffer.length() - 1; i >= 0; i--) {
            final char currentChar = strBuffer.charAt(i);
            if (Character.isWhitespace(currentChar)) {
                if (currentState == WhiteSpaceState.SPACE) {
                    strBuffer.delete(i, i + 1);
                } else if(currentChar != ' ') {
                    strBuffer.replace(i, i + 1, " ");
                }
                currentState = WhiteSpaceState.SPACE;
            } else {
                currentState = WhiteSpaceState.WORD;
            }
        }
    }

    /**
     * Trim whitespace from start of the string.
     * 
     * @param temp
     * @param termName
     * @return
     */
    private String trimSpaceAtStart(final String temp, final String termName) {
        if(termName != null && termName.charAt(termName.length() - 1) == ' ') {
            if(temp.charAt(0) == ' ') {
                return temp.substring(1);
            }
        }
        return temp;
    }

}