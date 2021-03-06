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
package org.brackit.server.node.index.path.impl;

import org.brackit.xquery.util.log.Logger;
import org.brackit.server.ServerException;
import org.brackit.server.io.buffer.PageID;
import org.brackit.server.node.index.definition.IndexDef;
import org.brackit.server.node.index.path.PathIndex;
import org.brackit.server.node.txnode.IndexEncoder;
import org.brackit.server.store.OpenMode;
import org.brackit.server.store.SearchMode;
import org.brackit.server.store.index.Index;
import org.brackit.server.store.index.IndexIterator;
import org.brackit.server.tx.Tx;
import org.brackit.server.util.sort.MergeSort;
import org.brackit.server.util.sort.Sort;
import org.brackit.server.util.sort.SortItem;
import org.brackit.xquery.node.parser.DefaultListener;
import org.brackit.xquery.node.parser.SubtreeListener;
import org.brackit.xquery.node.stream.filter.Filter;
import org.brackit.xquery.xdm.DocumentException;
import org.brackit.xquery.xdm.Node;
import org.brackit.xquery.xdm.Stream;

/**
 * @author Karsten Schmidt
 * @author Sebastian Baechle
 * 
 */
public class PathIndexBuilder<E extends Node<E>> extends DefaultListener<E>
		implements SubtreeListener<E> {
	private static final Logger log = Logger.getLogger(PathIndex.class);

	private final Tx tx;

	private final Index index;

	private final int containerNo;

	private final IndexDef indexDef;

	private final IndexEncoder<E> encoder;

	private final Filter<? super E> filter;

	private final Sort sorter;

	// statistics
	protected long indexedRecords;
	protected long indexedKeyBytes;

	public PathIndexBuilder(Tx tx, Index index, int containerNo,
			IndexDef indexDef, IndexEncoder<E> encoder, Filter<? super E> filter) {
		this.tx = tx;
		this.index = index;
		this.containerNo = containerNo;
		this.indexDef = indexDef;
		this.encoder = encoder;
		this.filter = filter;

		sorter = new MergeSort(encoder.getKeyType(), encoder.getValueType());
	}

	@Override
	public <T extends E> void attribute(T node) throws DocumentException {
		if (filter != null && !filter.filter(node)) {
			byte[] key = encoder.encodeKey(node);
			byte[] value = encoder.encodeValue(node);

			// update statistics
			this.indexedRecords++;
			this.indexedKeyBytes += key.length;

			// add to sorter
			try {
				sorter.add(new SortItem(key, value));
			} catch (ServerException e) {
				throw new DocumentException(e);
			}
		}
	}

	@Override
	public void end() throws DocumentException {
		Stream<? extends SortItem> sorted = null;

		try {
			long start = System.nanoTime();
			sorted = sorter.sort();
			long medium = System.nanoTime();

			// TODO: what is faster enforcing unique index or not enforcing
			// since we do not have uniqueness!
			PageID rootPageID = index.createIndex(tx, containerNo, encoder
					.getKeyType(), encoder.getValueType(), true, true);

			IndexIterator iterator = index.open(tx, rootPageID,
					SearchMode.FIRST, null, null, OpenMode.LOAD);
			iterator.triggerStatistics();

			SortItem item;
			while ((item = sorted.next()) != null) {
				iterator.insert(item.getKey(), item.getValue());
				iterator.next();
			}
			iterator.close();
			indexDef.setIndexStatistics(iterator.getStatistics());

			long end = System.nanoTime();

			if (log.isInfoEnabled()) {
				log.info(String.format("Building path index took %s ms.",
						((end - start) / 1000000)));
			}

			indexDef.createdAs(containerNo, rootPageID.value());
		} catch (ServerException e) {
			throw new DocumentException(e);
		} finally {
			if (sorted != null) {
				sorted.close();
			}
		}
	}

	@Override
	public void fail() throws DocumentException {
		sorter.errorCleanup();
	}

	@Override
	public <T extends E> void startElement(T node) throws DocumentException {
		if (filter != null && (!filter.filter(node))) {
			byte[] key = encoder.encodeKey(node);
			byte[] value = encoder.encodeValue(node);

			// update statistics
			this.indexedRecords++;
			this.indexedKeyBytes += key.length;

			// add to sorter
			try {
				sorter.add(new SortItem(key, value));
			} catch (ServerException e) {
				throw new DocumentException(e);
			}
		}
	}
}