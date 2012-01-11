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
 * DISCLAIMED. IN NO EVENT SHALL <COPYRIGHT HOLDER> BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.brackit.server.node.bracket;

import org.brackit.server.SysMockup;
import org.brackit.server.node.txnode.StorageSpec;
import org.brackit.server.tx.Tx;
import org.brackit.xquery.XMarkTestUnnestedJoin;
import org.brackit.xquery.node.parser.DocumentParser;
import org.brackit.xquery.xdm.Collection;
import org.brackit.xquery.xdm.DocumentException;

/**
 * @author Sebastian Baechle
 *
 */
public class BracketXMarkTestUnnestedJoin extends XMarkTestUnnestedJoin {

	protected SysMockup sm;
	protected BracketStore elStore;
	protected Tx tx;

	@Override
	public void setUp() throws Exception {
		sm = new SysMockup();
		elStore = new BracketStore(sm.bufferManager, sm.dictionary, sm.mls);
		tx = sm.taMgr.begin();
		tx.setLockDepth(0);
		super.setUp();
	}

	@Override
	protected Collection<?> createDoc(DocumentParser parser) throws DocumentException {
		StorageSpec spec = new StorageSpec("test", sm.dictionary);
		BracketCollection collection = new BracketCollection(tx, elStore);
		collection.create(spec, parser);
		return collection;
	}
}
