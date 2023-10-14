/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hop.ui.hopgui.perspective.explorer.file.types.svg;

import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.ui.hopgui.HopGui;
import org.apache.hop.ui.hopgui.file.HopFileTypePlugin;
import org.apache.hop.ui.hopgui.file.IHopFileType;
import org.apache.hop.ui.hopgui.file.IHopFileTypeHandler;
import org.apache.hop.ui.hopgui.file.empty.EmptyHopFileTypeHandler;
import org.apache.hop.ui.hopgui.perspective.explorer.ExplorerFile;
import org.apache.hop.ui.hopgui.perspective.explorer.ExplorerPerspective;
import org.apache.hop.ui.hopgui.perspective.explorer.file.IExplorerFileType;
import org.apache.hop.ui.hopgui.perspective.explorer.file.capabilities.FileTypeCapabilities;
import org.apache.hop.ui.hopgui.perspective.explorer.file.types.base.BaseExplorerFileType;

@HopFileTypePlugin(id = "SvgExplorerFileType", name = "SVG File Type", description = "SVG file handling in the explorer perspective", image = "ui/images/image.svg")
public class SvgExplorerFileType extends BaseExplorerFileType<SvgExplorerFileTypeHandler> implements IExplorerFileType<SvgExplorerFileTypeHandler> {

  public SvgExplorerFileType() {
    super("SVG file", ".svg", new String[] {"*.svg"}, new String[] {"SVG Files"},
        FileTypeCapabilities.getCapabilities(IHopFileType.CAPABILITY_CLOSE, IHopFileType.CAPABILITY_FILE_HISTORY));
  }

  @Override
  public SvgExplorerFileTypeHandler createFileTypeHandler(HopGui hopGui, ExplorerPerspective perspective, ExplorerFile file) {
    return new SvgExplorerFileTypeHandler(hopGui, perspective, file);
  }

  @Override
  public IHopFileTypeHandler newFile(HopGui hopGui, IVariables parentVariableSpace) throws HopException {
    return new EmptyHopFileTypeHandler();
  }
}
