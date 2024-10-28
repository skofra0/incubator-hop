/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hop.pipeline.transforms.javascript;

import org.apache.hop.compatibility.Value;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.pipeline.transform.BaseTransformData;
import org.apache.hop.pipeline.transform.ITransformData;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Script;
import org.mozilla.javascript.Scriptable;

@SuppressWarnings("java:S1104")
public class ScriptValuesData extends BaseTransformData implements ITransformData {
  public Context cx;
  public Scriptable scope;
  public Script script;

  public int[] fieldsUsed;
  Value[] valuesUsed; // DEEM-MOD

  public IRowMeta outputRowMeta;
  public int[] replaceIndex;

  public ScriptValuesData() {
    super();
    cx = null;
    fieldsUsed = null;
  }

  public void check(int i) {
    System.out.println(i);
  }
}
