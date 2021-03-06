package org.edmcouncil.rdf_toolkit;

import org.openrdf.model.*;
import org.openrdf.rio.RDFHandlerException;

import javax.xml.namespace.QName;
import java.io.OutputStream;
import java.io.Writer;
import java.util.*;

/**
 * Equivalent to Sesame's built-in RDF/XML writer, but the triples are sorted into a consistent order.
 * In order to do the sorting, it must be possible to load all of the RDF statements into memory.
 * NOTE: comments are suppressed, as there isn't a clear way to sort them along with triples.
 */
public class SesameSortedRdfXmlWriter extends SesameSortedRDFWriter {
    // TODO: the 'out' parameter in 'write...' methods is not used, and should be refactored out of the code.  Perhaps.  One day.

//    private static final Logger logger = LoggerFactory.getLogger(SesameSortedRdfXmlWriter.class);

    private static final boolean useGeneratedPrefixes = true; // need to use namespace prefixes generated by the serializer in order to be able to convert all predicates to a QName, as required for RDF/XML.
    private static final Class collectionClass = Resource.class; // RDF/XML only allows "resources" in RDF collections

    /** Output stream for this RDF/XML writer. */
    // Note: this is an internal Java class, not part of the published API.  But easier than writing our own indenter here.
    private IndentingXMLStreamWriter output = null;

    /** Namespace prefix for the RDF namespace. */
    private String rdfPrefix = "rdf";
    private String xmlPrefix = "xml";

    /**
     * Creates an RDFWriter instance that will write sorted RDF/XML to the supplied output stream.
     *
     * @param out The OutputStream to write the RDF/XML to.
     */
    public SesameSortedRdfXmlWriter(OutputStream out) throws Exception {
        super(out);
        this.output = new IndentingXMLStreamWriter(out);
    }

    /**
     * Creates an RDFWriter instance that will write sorted RDF/XML to the supplied writer.
     *
     * @param writer The Writer to write the RDF/XML to.
     */
    public SesameSortedRdfXmlWriter(Writer writer) throws Exception {
        super(writer);
        this.output = new IndentingXMLStreamWriter(writer);
    }

    /**
     * Creates an RDFWriter instance that will write sorted RDF/XML to the supplied output stream.
     *
     * @param out The OutputStream to write the RDF/XML to.
     * @param options options for the RDF/XML writer.
     */
    public SesameSortedRdfXmlWriter(OutputStream out, Map<String, Object> options) throws Exception {
        super(out, options);
        String indent = options.containsKey("indent") ? ((String) options.get("indent")) : null;
        this.output = new IndentingXMLStreamWriter(out, "UTF-8", indent, true);
    }

    /**
     * Creates an RDFWriter instance that will write sorted RDF/XML to the supplied writer.
     *
     * @param writer The Writer to write the RDF/XML to.
     * @param options options for the RDF/XML writer.
     */
    public SesameSortedRdfXmlWriter(Writer writer, Map<String, Object> options) throws Exception {
        super(writer, options);
        String indent = options.containsKey("indent") ? ((String) options.get("indent")) : null;
        this.output = new IndentingXMLStreamWriter(writer, indent, true);
    }

    /**
     * Signals the start of the RDF data. This method is called before any data
     * is reported.
     *
     * @throws org.openrdf.rio.RDFHandlerException If the RDF handler has encountered an unrecoverable error.
     */
    @Override
    public void startRDF() throws RDFHandlerException {
        super.startRDF();
    }

    /**
     * Signals the end of the RDF data. This method is called when all data has
     * been reported.
     *
     * @throws org.openrdf.rio.RDFHandlerException If the RDF handler has encountered an unrecoverable error.
     */
    @Override
    public void endRDF() throws RDFHandlerException {
        try {
            // Sort triples, etc.
            sortedOntologies = unsortedOntologies.toSorted(collectionClass);
            if (sortedOntologies.size() != unsortedOntologies.size()) {
                System.err.println("**** ontologies unexpectedly lost or gained during sorting: " + sortedOntologies.size() + " != " + unsortedOntologies.size());
                System.err.flush();
            }

            sortedTripleMap = unsortedTripleMap.toSorted(collectionClass);
            if (sortedTripleMap.fullSize() != unsortedTripleMap.fullSize()) {
                System.err.println("**** triples unexpectedly lost or gained during sorting: " + sortedTripleMap.fullSize() + " != " + unsortedTripleMap.fullSize());
                System.err.flush();
            }

            sortedBlankNodes = unsortedBlankNodes.toSorted(collectionClass);
            if (sortedBlankNodes.size() != unsortedBlankNodes.size()) {
                System.err.println("**** blank nodes unexpectedly lost or gained during sorting: " + sortedBlankNodes.size() + " != " + unsortedBlankNodes.size());
                System.err.flush();
            }

            super.endRDF();
        } catch (Throwable t) {
            throw new RDFHandlerException("unable to generate/write RDF output", t);
        }
    }

    private void writeSpecialComments(Writer out, String[] comments) throws Exception {
        if ((comments != null) && (comments.length >= 1)) {
            ArrayList<String> escapedComments = new ArrayList<String>();
            for (String comment : comments) {
                escapedComments.add(escapeCommentText(comment));
            }
            final String indent = output.getIndentationString();
            final String surroundString = "\n" + indent + "####";
            final String joinString = "\n" + indent + "## ";
            output.writeEOL(); // add extra EOL before comments
            output.writeComment(surroundString + joinString + String.join(joinString, escapedComments) + surroundString + "\n" + indent);
            output.writeEOL(); // add extra EOL after comments
        }
    }

    protected void writeHeader(Writer out, SortedTurtleObjectList importList, String[] leadingComments) throws Exception {
        // Get prefixes used for the XML
        rdfPrefix = reverseNamespaceTable.get(RDF_NS_URI);

        // Create a sorted list of namespace prefix mappings.
        TreeSet<String> prefixes = new TreeSet<String>(namespaceTable.keySet());

        // Write the XML prologue <?xml ... ?>
        output.writeStartDocument(output.getXmlEncoding(), "1.0");
        output.writeEOL();

        // Write the DTD subset, if required
        if (useDtdSubset) {
            output.startDTD("rdf:RDF");
            if (namespaceTable.size() > 0) {
                for (String prefix : prefixes) {
                    if (useGeneratedPrefixes || !generatedNamespaceTable.containsKey(prefix)) {
                        if (prefix.length() >= 1) {
                            output.writeDtdEntity(prefix, namespaceTable.get(prefix));
                        }
                    }
                }
            }
            output.endDTD();
        }

        // Open the root element.
        output.writeStartElement(rdfPrefix, "RDF", RDF_NS_URI); // <rdf:RDF>

        // Write the base IRI, if any.
        if (baseIri != null) {
            output.writeAttribute("xml", XML_NS_URI, "base", baseIri.stringValue());
        }

        // Write the namespace declarations into the root element.
        if (namespaceTable.size() > 0) {
            for (String prefix : prefixes) {
                if ((useGeneratedPrefixes || !generatedNamespaceTable.containsKey(prefix)) && !"xml".equals(prefix)) {
                    if (prefix.length() >= 1) {
                        output.writeNamespace(prefix, namespaceTable.get(prefix));
                    } else {
                        output.writeDefaultNamespace(namespaceTable.get(prefix));
                    }
                }
            }
        } else { // create RDF namespace at a minimum
            output.writeNamespace(rdfPrefix, RDF_NS_URI);
        }

        addDefaultNamespacePrefixIfMissing(XML_NS_URI, "xml"); // RDF/XML sometimes uses the 'xml' prefix, e.g. xml:lang.  This prefix is never declared explicitly.
        reverseNamespaceTable.put(XML_NS_URI, "xml"); // need to update reverse namespace table manually

        // NOTE: have decided I don't need to preserve whitespace in attributes as I don't produce whitespace-sensitive attributes in RDF/XML.  Also, apparently some less-than-conformant XML applications have problems with it.
        // output.writeAttribute("xml", XML_NS_URI, "space", "preserve"); // make sure whitespace is preserved, for consistency of formatting

        output.writeCharacters(""); // force writing of closing angle bracket in root element open tag
        output.writeEOL(); // add extra EOL after root element

        writeSpecialComments(out, leadingComments);
    }

    protected void writeSubjectSeparator(Writer out) throws Exception {
        // nothing to do here for RDF/XML
    }

    protected void writeSubjectTriples(Writer out, Resource subject) throws Exception {
        SortedTurtlePredicateObjectMap poMap = sortedTripleMap.get(subject);
        if (poMap == null) { poMap = new SortedTurtlePredicateObjectMap(); }
        // Try to determine whether to use <rdf:Description> or an element based on rdf:type value.
        SortedTurtleObjectList subjectRdfTypes = (SortedTurtleObjectList) poMap.get(rdfType); // needed to determine if a type can be used as the XML element name
        if (subjectRdfTypes != null) { subjectRdfTypes = (SortedTurtleObjectList) subjectRdfTypes.clone(); } // make a copy so we can remove values safely
        if ((subjectRdfTypes != null) && (subjectRdfTypes.size() >= 2) && subjectRdfTypes.contains(owlNamedIndividual)) { // ignore owl:NamedIndividual for the purposes of determining what type to use an an element name in RDF/XML
            subjectRdfTypes.remove(owlNamedIndividual);
        }
        if ((subjectRdfTypes != null) && (subjectRdfTypes.size() >= 2) && subjectRdfTypes.contains(owlThing)) { // ignore owl:Thing for the purposes of determining what type to use an an element name in RDF/XML
            subjectRdfTypes.remove(owlThing);
        }
        IRI enclosingElementIRI = rdfDescription; // default value
        QName enclosingElementQName = convertIriToQName(enclosingElementIRI, useGeneratedPrefixes);
        if ((subjectRdfTypes != null) && (subjectRdfTypes.size() == 1)) {
            Value subjectRdfTypeValue = subjectRdfTypes.first();
            if (subjectRdfTypeValue instanceof IRI) {
                QName subjectRdfTypeQName = convertIriToQName((IRI) subjectRdfTypeValue, useGeneratedPrefixes);
                if (subjectRdfTypeQName != null) {
                    enclosingElementIRI = (IRI) subjectRdfTypeValue;
                    enclosingElementQName = subjectRdfTypeQName;
                }
            }
        }

        // Write enclosing element.
        // The variation used for "rdf:about", or "rdf:nodeID", depends on settings and also whether the subject is a blank node or not.
        output.writeStartElement(enclosingElementQName.getPrefix(), enclosingElementQName.getLocalPart(), enclosingElementQName.getNamespaceURI());
        if (subject instanceof BNode) {
            if (!inlineBlankNodes) {
                output.writeAttribute(reverseNamespaceTable.get(RDF_NS_URI), RDF_NS_URI, "nodeID", blankNodeNameMap.get(subject));
            }
        } else if (subject instanceof IRI) {
            output.writeStartAttribute(reverseNamespaceTable.get(RDF_NS_URI), RDF_NS_URI, "about");
            QName subjectQName = convertIriToQName((IRI)subject, useGeneratedPrefixes);
            if ((subjectQName != null) && (subjectQName.getPrefix() != null) && (subjectQName.getPrefix().length() >= 1)) { // if a prefix is defined, write out the subject QName using an entity reference
                output.writeAttributeEntityRef(subjectQName.getPrefix());
                output.writeAttributeCharacters(((IRI) subject).getLocalName());
            } else { // just write the whole subject IRI
                output.writeAttributeCharacters(subject.toString());
            }
            output.endAttribute();
        } else {
            output.writeAttribute(reverseNamespaceTable.get(RDF_NS_URI), RDF_NS_URI, "about", subject.stringValue()); // this shouldn't occur, but ...
        }

        // Write predicate/object pairs rendered first.
        for (IRI predicate : firstPredicates) {
            if (poMap.containsKey(predicate)) {
                SortedTurtleObjectList values = poMap.get(predicate);
                if (values != null) { values = (SortedTurtleObjectList) values.clone(); } // make a copy so we don't delete anything from the original
                if (predicate == rdfType) { // assumes that rdfType is one of the firstPredicates
                    values.remove(enclosingElementIRI); // no need to state type explicitly if it has been used as an enclosing element name
                }
                if (values.size() >= 1) {
                    writePredicateAndObjectValues(out, predicate, values);
                }
            }
        }

        // Write other predicate/object pairs.
        for (IRI predicate : poMap.keySet()) {
            if (!firstPredicates.contains(predicate)) {
                SortedTurtleObjectList values = poMap.get(predicate);
                writePredicateAndObjectValues(out, predicate, values);
            }
        }

        // Close enclosing element.
        output.writeEndElement();
        if (!inlineBlankNodes || !(subject instanceof BNode)) {
            output.writeEOL();
        }
    }

    protected void writePredicateAndObjectValues(Writer out, IRI predicate, SortedTurtleObjectList values) throws Exception {
        // Get prefixes used for the XML
        rdfPrefix = reverseNamespaceTable.get(RDF_NS_URI);
        xmlPrefix = reverseNamespaceTable.get(XML_NS_URI);

        QName predicateQName = convertIriToQName(predicate, useGeneratedPrefixes);
        for (Value value : values) {
            if (inlineBlankNodes && (value instanceof BNode)) {
                BNode bnode = (BNode) value;
                if (isCollection(bnode, collectionClass)) {
                    ArrayList<Value> members = getCollectionMembers(bnode, collectionClass);
                    output.writeStartElement(predicateQName.getPrefix(), predicateQName.getLocalPart(), predicateQName.getNamespaceURI());
                    QName rdfParseTypeQName = convertIriToQName(rdfParseType, useGeneratedPrefixes);
                    output.writeAttribute(rdfParseTypeQName.getPrefix(), rdfParseTypeQName.getNamespaceURI(), rdfParseTypeQName.getLocalPart(), "Collection");
                    for (Value member : members) {
                        if (member instanceof BNode) {
                            writeSubjectTriples(out, (Resource) member);
                        } else if (member instanceof IRI) {
                            QName rdfDescriptionQName = convertIriToQName(rdfDescription, useGeneratedPrefixes);
                            QName rdfAboutQName = convertIriToQName(rdfAbout, useGeneratedPrefixes);
                            output.writeStartElement(rdfDescriptionQName.getPrefix(), rdfDescriptionQName.getLocalPart(), rdfDescriptionQName.getNamespaceURI());
                            QName memberQName = convertIriToQName((IRI) member, useGeneratedPrefixes);
                            if ((memberQName == null) || (memberQName.getPrefix() == null) || (memberQName.getPrefix().length() < 1)) {
                                output.writeAttribute(rdfAboutQName.getPrefix(), rdfAboutQName.getNamespaceURI(), rdfAboutQName.getLocalPart(), member.stringValue());
                            } else {
                                output.writeStartAttribute(rdfAboutQName.getPrefix(), rdfAboutQName.getNamespaceURI(), rdfAboutQName.getLocalPart());
                                output.writeAttributeEntityRef(memberQName.getPrefix());
                                output.writeAttributeCharacters(memberQName.getLocalPart());
                                output.endAttribute();
                            }
                            output.writeEndElement();
                        } else {
                            QName rdfDescriptionQName = convertIriToQName(rdfDescription, useGeneratedPrefixes);
                            output.writeStartElement(rdfDescriptionQName.getPrefix(), rdfDescriptionQName.getLocalPart(), rdfDescriptionQName.getNamespaceURI());
                            if (member instanceof Literal) {
                                if (((Literal)member).getDatatype() != null) {
                                    boolean useExplicit = (stringDataTypeOption == SesameSortedRDFWriterFactory.StringDataTypeOptions.explicit) || !(xsString.equals(((Literal)member).getDatatype()) || rdfLangString.equals(((Literal)member).getDatatype()));
                                    if (useExplicit) {
                                        output.writeStartAttribute(rdfPrefix, RDF_NS_URI, "datatype");
                                        QName datatypeQName = convertIriToQName(((Literal) member).getDatatype(), useGeneratedPrefixes);
                                        if ((datatypeQName == null) || (datatypeQName.getPrefix() == null) || (datatypeQName.getPrefix().length() < 1)) {
                                            output.writeAttributeCharacters(((Literal) member).getDatatype().stringValue());
                                        } else {
                                            output.writeAttributeEntityRef(datatypeQName.getPrefix());
                                            output.writeAttributeCharacters(datatypeQName.getLocalPart());
                                        }
                                        output.endAttribute();
                                    }
                                }
                                if (((Literal)member).getLanguage().isPresent()) {
                                    output.writeAttribute(xmlPrefix, XML_NS_URI, "lang", ((Literal)member).getLanguage().get());
                                }
                                output.writeCharacters(member.stringValue());
                            } else {
                                output.writeCharacters(member.stringValue());
                            }
                            output.writeEndElement();
                        }
                    }
                    output.writeEndElement();
                } else {
                    output.writeStartElement(predicateQName.getPrefix(), predicateQName.getLocalPart(), predicateQName.getNamespaceURI());
                    writeSubjectTriples(out, bnode);
                    output.writeEndElement();
                }
            } else { // not an inline blank node`
                if ((value instanceof BNode) || (value instanceof IRI)) {
                    output.writeEmptyElement(predicateQName.getPrefix(), predicateQName.getLocalPart(), predicateQName.getNamespaceURI());
                } else {
                    output.writeStartElement(predicateQName.getPrefix(), predicateQName.getLocalPart(), predicateQName.getNamespaceURI());
                }
                if (value instanceof BNode) {
                    output.writeAttribute(rdfPrefix, RDF_NS_URI, "nodeID", blankNodeNameMap.get(value));
                } else if (value instanceof IRI) {
                    output.writeStartAttribute(rdfPrefix, RDF_NS_URI, "resource");
                    QName iriQName = convertIriToQName((IRI) value, useGeneratedPrefixes);
                    if (iriQName == null) {
                        output.writeAttributeCharacters(value.stringValue());
                    } else {
                        if ((iriQName.getPrefix() != null) && (iriQName.getPrefix().length() >= 1)) {
                            output.writeAttributeEntityRef(iriQName.getPrefix());
                            output.writeAttributeCharacters(iriQName.getLocalPart());
                        } else {
                            output.writeAttributeCharacters(value.stringValue());
                        }
                    }
                    output.endAttribute();
                } else if (value instanceof Literal) {
                    if (((Literal)value).getDatatype() != null) {
                        boolean useExplicit = (stringDataTypeOption == SesameSortedRDFWriterFactory.StringDataTypeOptions.explicit) || !(xsString.equals(((Literal)value).getDatatype()) || rdfLangString.equals(((Literal)value).getDatatype()));
                        if (useExplicit) {
                            output.writeStartAttribute(rdfPrefix, RDF_NS_URI, "datatype");
                            QName datatypeQName = convertIriToQName(((Literal) value).getDatatype(), useGeneratedPrefixes);
                            if ((datatypeQName == null) || (datatypeQName.getPrefix() == null) || (datatypeQName.getPrefix().length() < 1)) {
                                output.writeAttributeCharacters(((Literal) value).getDatatype().stringValue());
                            } else {
                                output.writeAttributeEntityRef(datatypeQName.getPrefix());
                                output.writeAttributeCharacters(datatypeQName.getLocalPart());
                            }
                            output.endAttribute();
                        }
                    }
                    if (((Literal)value).getLanguage().isPresent()) {
                        output.writeAttribute(xmlPrefix, XML_NS_URI, "lang", ((Literal)value).getLanguage().get());
                    }
                    output.writeCharacters(value.stringValue().trim()); // trim the value, because leading/closing spaces will be lost on subsequent parses/serialisations.
                } else {
                    output.writeCharacters(value.stringValue().trim()); // trim the value, because leading/closing spaces will be lost on subsequent parses/serialisations.
                }
                output.writeEndElement();
            }
        }
    }

    protected void writeFooter(Writer out, String[] trailingComments) throws Exception {
        writeSpecialComments(out, trailingComments);

        output.writeEndElement(); // </rdf:RDF>
        output.writeEndDocument();
    }

    public static String escapeCommentText(String comment) {
        if (comment == null) { return null; }
        return comment.replaceAll("--", "&#x2D;&#x2D;");
    }

}
