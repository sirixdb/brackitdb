/*
 * [New BSD License]
 * Copyright (c) 2011-2012, Brackit Project Team <info@brackit.org>  
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of the Brackit Project Team nor the
 *       names of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.brackit.server.xquery.function.xmark;

import java.text.SimpleDateFormat;

import org.brackit.server.metadata.DBCollection;
import org.brackit.server.metadata.masterDocument.Indexes;
import org.brackit.server.metadata.vocabulary.DictionaryMgr;
import org.brackit.server.node.index.IndexController;
import org.brackit.server.node.index.definition.IndexDef;
import org.brackit.server.node.txnode.TXNode;
import org.brackit.server.store.SearchMode;
import org.brackit.server.xquery.function.FunUtil;
import org.brackit.server.xquery.function.xmark.util.AxisPredicate;
import org.brackit.server.xquery.function.xmark.util.DocumentScan;
import org.brackit.server.xquery.function.xmark.util.ExprOp;
import org.brackit.server.xquery.function.xmark.util.MergeJoin;
import org.brackit.server.xquery.function.xmark.util.NavigationExpander;
import org.brackit.server.xquery.function.xmark.util.NavigationExpander.Ancestor;
import org.brackit.server.xquery.function.xmark.util.NavigationExpander.Attribute;
import org.brackit.server.xquery.function.xmark.util.NavigationExpander.Child;
import org.brackit.server.xquery.function.xmark.util.NestExpr;
import org.brackit.server.xquery.function.xmark.util.NodeTest;
import org.brackit.server.xquery.function.xmark.util.Select;
import org.brackit.server.xquery.function.xmark.util.Split;
import org.brackit.server.xquery.function.xmark.util.StreamOperator;
import org.brackit.server.xquery.function.xmark.util.TextTest;
import org.brackit.xquery.ErrorCode;
import org.brackit.xquery.QueryContext;
import org.brackit.xquery.QueryException;
import org.brackit.xquery.Tuple;
import org.brackit.xquery.atomic.Atomic;
import org.brackit.xquery.atomic.QNm;
import org.brackit.xquery.atomic.Str;
import org.brackit.xquery.expr.BoundVariable;
import org.brackit.xquery.expr.VCmpExpr;
import org.brackit.xquery.function.AbstractFunction;
import org.brackit.xquery.module.StaticContext;
import org.brackit.xquery.node.stream.filter.Filter;
import org.brackit.xquery.operator.Cursor;
import org.brackit.xquery.util.Cmp;
import org.brackit.xquery.util.annotation.FunctionAnnotation;
import org.brackit.xquery.util.path.Path;
import org.brackit.xquery.util.path.PathException;
import org.brackit.xquery.xdm.Axis;
import org.brackit.xquery.xdm.DocumentException;
import org.brackit.xquery.xdm.Kind;
import org.brackit.xquery.xdm.Sequence;
import org.brackit.xquery.xdm.Signature;
import org.brackit.xquery.xdm.type.AtomicType;
import org.brackit.xquery.xdm.type.Cardinality;
import org.brackit.xquery.xdm.type.SequenceType;

/**
 * @author Sebastian Baechle
 * 
 *         <p>
 *         let $auction := doc("auction.xml") return for $b in
 *         $auction/site/people/person[@id = "person0"] return $b/name/text()
 *         </p>
 * 
 */
@FunctionAnnotation(description = "Evaluates XMark query 1 for the given document using a particular strategy:\n"
		+ "1 - scan (default)\n"
		+ "2 - multiple scans\n"
		+ "3 - element index with navigation\n"
		+ "4 - path and content index with navigation\n"
		+ "5 - cas index with navigation", parameters = { "$document",
		"strategy" })
public class XMarkQuery1 extends AbstractFunction {

	protected static final SimpleDateFormat dateFormat = new SimpleDateFormat(
			"MM/dd/yyyy");

	protected static final SimpleDateFormat timeFormat = new SimpleDateFormat(
			"HH:mm:ss");

	public static final QNm DEFAULT_NAME = new QNm(XMarkFun.XMARK_NSURI,
			XMarkFun.XMARK_PREFIX, "query1");

	public XMarkQuery1() {
		super(DEFAULT_NAME, new Signature(new SequenceType(AtomicType.STR,
				Cardinality.One), new SequenceType(AtomicType.STR,
				Cardinality.One), new SequenceType(AtomicType.INR,
				Cardinality.One)), true);
	}

	@Override
	public Sequence execute(StaticContext sctx, QueryContext ctx,
			Sequence[] args) throws QueryException {
		String docname = ((Atomic) args[0]).stringValue();
		TXNode<?> doc = ((DBCollection<?>) ctx.getStore().lookup(docname))
				.getDocument();
		int strategy = FunUtil.getInt(args, 1, "STRATEGY", 1, new int[] {
				1, 2, 3, 4, 5 }, false);

		StringBuilder out = new StringBuilder();

		long start = System.currentTimeMillis();
		switch (strategy) {
		case 1:
			evaluateWithDocumentScan(ctx, doc, out, true);
			break;
		case 2:
			evaluateWithDocumentScan(ctx, doc, out, false);
			break;
		case 3:
			evaluateWithNameIndex(ctx, doc, out);
			break;
		case 4:
			evaluateWithPathAndContentIndex(ctx, doc, out);
			break;
		case 5:
			evaluateWithCASIndex(ctx, doc, out);
			break;
		default:
			throw new QueryException(ErrorCode.BIT_DYN_RT_ILLEGAL_STATE_ERROR,
					"Unknown strategy: %s", strategy);
		}
		long end = System.currentTimeMillis();

		Str result = new Str(out.toString());

		// HashMap<String, Double> map = new HashMap<String, Double>();
		// map.put("queryT", (double) end - start);
		//
		// double lockCount = 0;
		// for (LockServiceStatistics statistics :
		// ctx.getTX().getLockCB().getStatistics())
		// {
		// lockCount +=
		// statistics.getLockTypeStatistics().iterator().next().getLockCount();
		// result.addInfoObject(statistics);
		// }
		// map.put("lockN", lockCount);
		//
		// long lockTIme =
		// ctx.getTX().getStatistics().getTime(TxStatistics.LOCK_REQUEST_TIME);
		// long ioTime =
		// ctx.getTX().getStatistics().getTime(TxStatistics.IO_FETCH_TIME);
		// map.put("lockT", (double) lockTIme);
		// map.put("IOT", (double) ioTime);
		// map.put("IOP", (ioTime != 0) ? ioTime / (double) (end - start) : 0);
		// map.put("lockP", (lockTIme != 0) ? lockTIme / (double) (end - start)
		// : 0);
		// result.addInfoObject(map);

		return result;
	}

	private void evaluateWithDocumentScan(QueryContext ctx, TXNode<?> doc,
			StringBuilder out, boolean shareDocumentScan)
			throws QueryException, DocumentException {
		Cursor docScan = new DocumentScan(doc.getFirstChild());
		Split split = new Split(docScan);

		long start = System.currentTimeMillis();
		Cursor in1 = (shareDocumentScan) ? split.createClient()
				: new DocumentScan(doc.getFirstChild());
		in1 = new Select(in1, new NodeTest("site", false));

		Cursor in2 = (shareDocumentScan) ? split.createClient()
				: new DocumentScan(doc.getFirstChild());
		in2 = new Select(in2, new NodeTest("people", false));

		Cursor in3 = (shareDocumentScan) ? split.createClient()
				: new DocumentScan(doc.getFirstChild());
		in3 = new Select(in3, new NodeTest("person", false));

		Cursor in4 = (shareDocumentScan) ? split.createClient()
				: new DocumentScan(doc.getFirstChild());
		in4 = new Select(in4, new NodeTest("id", true));
		in4 = new ExprOp(in4, new Str("person0"));
		in4 = new Select(in4, new NestExpr(new VCmpExpr(Cmp.eq,
				new BoundVariable(new QNm("l"), 0), new BoundVariable(new QNm(
						"r"), 1)), new int[][] { new int[] { 0, 2 },
				new int[] { 1 } }), 0);

		Cursor in5 = (shareDocumentScan) ? split.createClient()
				: new DocumentScan(doc.getFirstChild());
		in5 = new Select(in5, new NodeTest("name", false));

		Cursor in6 = (shareDocumentScan) ? split.createClient()
				: new DocumentScan(doc.getFirstChild());
		in6 = new Select(in6, new TextTest());

		Cursor join;
		join = new MergeJoin(in1, in2, new AxisPredicate(Axis.PARENT,
				new BoundVariable(new QNm("l"), 0), new BoundVariable(new QNm(
						"r"), 1)), 1);
		join = new MergeJoin(join, in3, new AxisPredicate(Axis.PARENT,
				new BoundVariable(new QNm("l"), 0), new BoundVariable(new QNm(
						"r"), 1)), 1);
		join = new MergeJoin(join, in4, new AxisPredicate(Axis.PARENT,
				new BoundVariable(new QNm("l"), 0), new BoundVariable(new QNm(
						"r"), 1)), 0);
		join = new MergeJoin(join, in5, new AxisPredicate(Axis.PARENT,
				new BoundVariable(new QNm("l"), 0), new BoundVariable(new QNm(
						"r"), 1)));
		join = new MergeJoin(join, in6, new AxisPredicate(Axis.PARENT,
				new BoundVariable(new QNm("l"), 1), new BoundVariable(new QNm(
						"r"), 2)), new int[] { 0 }, new int[] { 0 }, 0, 2);

		Cursor plan = join;

		plan.open(ctx);
		for (Tuple tuple = plan.next(ctx); tuple != null; tuple = plan
				.next(ctx)) {
			out.append(tuple);
		}
		plan.close(ctx);
		long end = System.currentTimeMillis();
	}

	private void evaluateWithNameIndex(QueryContext ctx, TXNode<?> doc,
			StringBuilder out) throws QueryException, DocumentException {
		QNm site = new QNm("site");
		QNm people = new QNm("people");
		QNm person = new QNm("person");
		IndexDef nameIndex = doc.getCollection().get(Indexes.class)
				.findNameIndex(site, people, person);

		if (nameIndex == null) {
			throw new DocumentException("No name index found");
		}

		int nameIndexNo = nameIndex.getID();
		IndexController<?> indexController = doc.getCollection()
				.getIndexController();
		Cursor in1 = new StreamOperator(indexController.openNameIndex(
				nameIndexNo, site, SearchMode.FIRST));
		Cursor in2 = new StreamOperator(indexController.openNameIndex(
				nameIndexNo, people, SearchMode.FIRST));

		Cursor in3 = new StreamOperator(indexController.openNameIndex(
				nameIndexNo, person, SearchMode.FIRST));

		Cursor join;
		join = new MergeJoin(in1, in2, new AxisPredicate(Axis.PARENT,
				new BoundVariable(new QNm("l"), 0), new BoundVariable(new QNm(
						"r"), 1)), 1);
		join = new MergeJoin(join, in3, new AxisPredicate(Axis.PARENT,
				new BoundVariable(new QNm("l"), 0), new BoundVariable(new QNm(
						"r"), 1)), 1);

		NavigationExpander attributeExpander = new NavigationExpander(join, 0,
				new Attribute(null, new Str("id"), null), true);
		Select select = new Select(attributeExpander,
				new NestExpr(new VCmpExpr(Cmp.eq, new BoundVariable(
						new QNm("l"), 0), new Str("person0")),
						new int[][] { new int[] { 1, 2 } }), 0);

		NavigationExpander nameExpander = new NavigationExpander(select, 0,
				new Child(null, new Str("name"), null, Kind.ELEMENT), true);
		NavigationExpander textExpander = new NavigationExpander(nameExpander,
				1, new Child(null, null, null, Kind.TEXT), true, 0, 2);

		Cursor plan = textExpander;

		plan.open(ctx);
		for (Tuple tuple = plan.next(ctx); tuple != null; tuple = plan
				.next(ctx)) {
			out.append(tuple);
		}
		plan.close(ctx);
	}

	private void evaluateWithPathAndContentIndex(QueryContext ctx,
			TXNode<?> doc, StringBuilder out) throws QueryException {
		IndexDef pathIndex = doc.getCollection().get(Indexes.class)
				.findPathIndex(Path.parse("/site/people/person"));

		// IndexDef contentIndex = doc.getCollection().get(Indexes.class)
		// .findContentIndex();
		// TODO
		IndexDef contentIndex = null;

		if (pathIndex == null) {
			throw new DocumentException("No path index found");
		}

		if (contentIndex == null) {
			throw new DocumentException("No content index found");
		}

		int pathIndexNo = pathIndex.getID();
		int contentIndexNo = contentIndex.getID();

		IndexController<?> indexController = doc.getCollection()
				.getIndexController();
		DictionaryMgr dictionary = doc.getCollection().getDictionary();
		Filter filter = indexController.createPathFilter("/site/people/person");
		Cursor in1 = new StreamOperator(indexController.openPathIndex(
				pathIndexNo, filter, SearchMode.FIRST));

		// Cursor in2 = new StreamOperator(indexController.openContentIndex(
		// contentIndexNo, new Str("person0"), new Str("person0"), true,
		// true, SearchMode.GREATER_OR_EQUAL));
		// TODO
		Cursor in2 = null;

		MergeJoin join = new MergeJoin(in1, in2, new AxisPredicate(
				Axis.ANCESTOR, new BoundVariable(new QNm("l"), 0),
				new BoundVariable(new QNm("r"), 1)), 0);

		NavigationExpander nameExpander = new NavigationExpander(join, 0,
				new Child(null, new Str("name"), null, Kind.ELEMENT), true);
		NavigationExpander textExpander = new NavigationExpander(nameExpander,
				1, new Child(null, null, null, Kind.TEXT), true, 0, 2);

		Cursor plan = textExpander;

		plan.open(ctx);
		for (Tuple tuple = plan.next(ctx); tuple != null; tuple = plan
				.next(ctx)) {
			out.append(tuple);
		}
		plan.close(ctx);
	}

	private void evaluateWithCASIndex(QueryContext ctx, TXNode<?> doc,
			StringBuilder out) throws QueryException, DocumentException,
			PathException {
		IndexDef casIndex = doc.getCollection().get(Indexes.class)
				.findCASIndex(Path.parse("/site/people/person/@id"));

		if (casIndex == null) {
			throw new DocumentException("No CAS index found");
		}

		int pathIndexNo = casIndex.getID();

		IndexController<?> indexController = doc.getCollection()
				.getIndexController();
		DictionaryMgr dictionary = doc.getCollection().getDictionary();
		Filter filter = indexController
				.createPathFilter("/site/people/person/@id");
		Cursor in1 = new StreamOperator(indexController.openCASIndex(
				pathIndexNo, filter, new Str("person0"), new Str("person0"),
				true, true, SearchMode.GREATER_OR_EQUAL));

		NavigationExpander ancestorExpander = new NavigationExpander(in1, 0,
				new Ancestor(null, null, null, null, 1), true, 1);
		NavigationExpander nameExpander = new NavigationExpander(
				ancestorExpander, 0, new Child(null, new Str("name"), null,
						Kind.ELEMENT), true);
		NavigationExpander textExpander = new NavigationExpander(nameExpander,
				1, new Child(null, null, null, Kind.TEXT), true, 0, 2);

		Cursor plan = textExpander;

		plan.open(ctx);
		for (Tuple tuple = plan.next(ctx); tuple != null; tuple = plan
				.next(ctx)) {
			out.append(tuple);
		}
		plan.close(ctx);
	}
}
