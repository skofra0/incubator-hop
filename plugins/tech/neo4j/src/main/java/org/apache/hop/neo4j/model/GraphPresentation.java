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
package org.apache.hop.neo4j.model;

import org.apache.hop.metadata.api.HopMetadataProperty;

public class GraphPresentation {

  @HopMetadataProperty
  protected int x;

  @HopMetadataProperty
  protected int y;

  public GraphPresentation() {}

  public GraphPresentation(int x, int y) {
    this();
    this.x = x;
    this.y = y;
  }

  @Override
  public GraphPresentation clone() {
    return new GraphPresentation(x, y);
  }

  /**
   * Gets x
   *
   * @return value of x
   */
  public int getX() {
    return x;
  }

  /** @param x The x to set */
  public void setX(int x) {
    this.x = x;
  }

  /**
   * Gets y
   *
   * @return value of y
   */
  public int getY() {
    return y;
  }

  /** @param y The y to set */
  public void setY(int y) {
    this.y = y;
  }
}
