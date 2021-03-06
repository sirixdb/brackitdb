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
package org.brackit.server.xquery.compiler.optimizer;

import java.util.Map;

import org.brackit.server.metadata.manager.MetaDataMgr;
import org.brackit.server.tx.Tx;
import org.brackit.server.xquery.compiler.optimizer.walker.MultiChildStep;
import org.brackit.xquery.QueryException;
import org.brackit.xquery.atomic.QNm;
import org.brackit.xquery.atomic.Str;
import org.brackit.xquery.compiler.AST;
import org.brackit.xquery.compiler.optimizer.Stage;
import org.brackit.xquery.compiler.optimizer.TopDownOptimizer;
import org.brackit.xquery.module.StaticContext;

/**
 * @author Sebastian Baechle
 * 
 */
public class DBOptimizer extends TopDownOptimizer {

	public DBOptimizer(Map<QNm, Str> options, MetaDataMgr mdm, Tx tx) {
		super(options);
		// perform index matching as last step
		getStages().add(new Stage() {
			@Override
			public AST rewrite(StaticContext sctx, AST ast)
					throws QueryException {
				ast = new MultiChildStep(sctx).walk(ast);
				return ast;
			}
			
		});
		getStages().add(new IndexMatching(mdm, tx));
	}

	private static class IndexMatching implements Stage {
		private final MetaDataMgr mdm;
		private final Tx tx;

		public IndexMatching(MetaDataMgr mdm, Tx tx) {
			this.mdm = mdm;
			this.tx = tx;
		}

		@Override
		public AST rewrite(StaticContext sctx, AST ast) throws QueryException {
			// TODO add rules for index resolution here
			return ast;
		}
	}
}
