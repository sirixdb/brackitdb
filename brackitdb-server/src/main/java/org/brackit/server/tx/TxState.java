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
package org.brackit.server.tx;

/**
 * @author Sebastian Baechle
 * 
 */
public enum TxState {
	/**
	 * transaction is active but was aborted, e.g, due to a deadlock
	 */
	ABORTED(true, false, true),

	/**
	 * transaction is active in rollback processing
	 */
	ROLLBACK(true, false, false),

	/**
	 * transaction is active in normal processing
	 */
	RUNNING(true, true, true),

	/**
	 * transaction committed and terminated
	 */
	COMMITTED(false, false, false),

	/**
	 * transaction was rolled back and terminated
	 */
	ROLLEDBACK(false, false, false);

	private final boolean active;
	private final boolean commitable;
	private final boolean rollbackable;

	private TxState(boolean active, boolean commitable, boolean rollbackable) {
		this.active = active;
		this.commitable = commitable;
		this.rollbackable = rollbackable;
	}

	public boolean isActive() {
		return this.active;
	}

	public boolean isCommitable() {
		return commitable;
	}

	public boolean isRollbackable() {
		return rollbackable;
	}
}
