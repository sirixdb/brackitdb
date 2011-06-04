/*
 * [New BSD License]
 * Copyright (c) 2011, Brackit Project Team <info@brackit.org>  
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of the <organization> nor the
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
package org.brackit.server.store.page.bracket;

/**
 * Object containing a simple DeweyID (consisting of an int array and a length indicator).
 * 
 * @author Martin Hiller
 *
 */
public class SimpleDeweyIDImpl implements SimpleDeweyID {
	
	private int[] divisions;
	private int length;
	
	/**
	 * Creates a simple DeweyID.
	 * @param divisions the DeweyID divisions
	 * @param length indicates how many divisions from the divisions array are valid
	 */
	public SimpleDeweyIDImpl(int[] divisions, int length) {
		this.divisions = divisions;
		this.length = length;
	}

	/**
	 * @see org.brackit.server.store.page.bracket.SimpleDeweyID#getDivisionValues()
	 */
	@Override
	public int[] getDivisionValues() {
		return divisions;
	}

	/**
	 * @see org.brackit.server.store.page.bracket.SimpleDeweyID#getNumberOfDivisions()
	 */
	@Override
	public int getNumberOfDivisions() {
		return length;
	}

	/**
	 * @see org.brackit.server.store.page.bracket.SimpleDeweyID#isAttribute()
	 */
	@Override
	public boolean isAttribute() {
		return length > 2 && divisions[length - 2] == 1;
	}

}