/*! ******************************************************************************
 *
 * Hop : The Hop Orchestration Platform
 *
 * Copyright (C) 2002-2018 by Hitachi Vantara : http://www.pentaho.com
 *
 *******************************************************************************
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 ******************************************************************************/

package org.apache.hop.pipeline.transforms.delete;

import org.apache.hop.core.CheckResult;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.Const;
import org.apache.hop.core.SqlStatement;
import org.apache.hop.core.annotations.Transform;
import org.apache.hop.core.database.Database;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.exception.HopTransformException;
import org.apache.hop.core.exception.HopXmlException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.DatabaseImpact;
import org.apache.hop.pipeline.Pipeline;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.*;
import org.w3c.dom.Node;

import java.util.List;

/**
 * This class takes care of deleting values in a table using a certain condition and values for input.
 *
 * @author Tom, Matt
 * @since 28-March-2006
 */
@Transform(
        id = "Delete",
        image = "delete.svg",
        i18nPackageName = "org.apache.hop.pipeline.transforms.delete",
        name = "BaseTransform.TypeLongDesc.Delete",
        description = "BaseTransform.TypeTooltipDesc.Delete",
        categoryDescription = "i18n:org.apache.hop.pipeline.transform:BaseTransform.Category.Output",
        documentationUrl = "https://www.project-hop.org/manual/latest/plugins/transforms/delete.html"
)
public class DeleteMeta extends BaseTransformMeta implements ITransformMeta<Delete, DeleteData> {
  private static final Class<?> PKG = DeleteMeta.class; // Needed by Translator

  /**
   * The target schema name
   */
  private String schemaName;

  /**
   * The lookup table name
   */
  private String tableName;

  /**
   * database connection
   */
  private DatabaseMeta databaseMeta;

  /**
   * which field in input stream to compare with?
   */
  private String[] keyStream;

  /**
   * field in table
   */
  private String[] keyLookup;

  /**
   * Comparator: =, <>, BETWEEN, ...
   */
  private String[] keyCondition;

  /**
   * Extra field for between...
   */
  private String[] keyStream2;

  /**
   * Commit size for inserts/updates
   */
  private String commitSize;

  public DeleteMeta() {
    super(); // allocate BaseTransformMeta
  }

  /**
   * @return Returns the commitSize.
   */
  public String getCommitSizeVar() {
    return commitSize;
  }

  /**
   * @return Returns the commitSize.
   * @deprecated use public String getCommitSizeVar() instead
   */
  @Deprecated
  public int getCommitSize() {
    return Integer.parseInt( commitSize );
  }

  /**
   * @param vs -
   *           variable space to be used for searching variable value
   *           usually "this" for a calling transform
   * @return Returns the commitSize.
   */
  public int getCommitSize( IVariables vs ) {
    // this happens when the transform is created via API and no setDefaults was called
    commitSize = ( commitSize == null ) ? "0" : commitSize;
    return Integer.parseInt( vs.environmentSubstitute( commitSize ) );
  }

  /**
   * @param commitSize The commitSize to set.
   * @deprecated use public void setCommitSize( String commitSize ) instead
   */
  @Deprecated
  public void setCommitSize( int commitSize ) {
    this.commitSize = Integer.toString( commitSize );
  }

  /**
   * @param commitSize The commitSize to set.
   */
  public void setCommitSize( String commitSize ) {
    this.commitSize = commitSize;
  }

  /**
   * @return Returns the database.
   */
  public DatabaseMeta getDatabaseMeta() {
    return databaseMeta;
  }

  /**
   * @param database The database to set.
   */
  public void setDatabaseMeta( DatabaseMeta database ) {
    this.databaseMeta = database;
  }

  /**
   * @return Returns the keyCondition.
   */
  public String[] getKeyCondition() {
    return keyCondition;
  }

  /**
   * @param keyCondition The keyCondition to set.
   */
  public void setKeyCondition( String[] keyCondition ) {
    this.keyCondition = keyCondition;
  }

  /**
   * @return Returns the keyLookup.
   */
  public String[] getKeyLookup() {
    return keyLookup;
  }

  /**
   * @param keyLookup The keyLookup to set.
   */
  public void setKeyLookup( String[] keyLookup ) {
    this.keyLookup = keyLookup;
  }

  /**
   * @return Returns the keyStream.
   */
  public String[] getKeyStream() {
    return keyStream;
  }

  /**
   * @param keyStream The keyStream to set.
   */
  public void setKeyStream( String[] keyStream ) {
    this.keyStream = keyStream;
  }

  /**
   * @return Returns the keyStream2.
   */
  public String[] getKeyStream2() {
    return keyStream2;
  }

  /**
   * @param keyStream2 The keyStream2 to set.
   */
  public void setKeyStream2( String[] keyStream2 ) {
    this.keyStream2 = keyStream2;
  }

  /**
   * @return Returns the tableName.
   */
  public String getTableName() {
    return tableName;
  }

  /**
   * @param tableName The tableName to set.
   */
  public void setTableName( String tableName ) {
    this.tableName = tableName;
  }

  public void loadXml( Node transformNode, IHopMetadataProvider metadataProvider ) throws HopXmlException {
    readData( transformNode, metadataProvider );
  }

  public void allocate( int nrkeys ) {
    keyStream = new String[ nrkeys ];
    keyLookup = new String[ nrkeys ];
    keyCondition = new String[ nrkeys ];
    keyStream2 = new String[ nrkeys ];
  }

  public Object clone() {
    DeleteMeta retval = (DeleteMeta) super.clone();
    int nrkeys = keyStream.length;

    retval.allocate( nrkeys );

    System.arraycopy( keyStream, 0, retval.keyStream, 0, nrkeys );
    System.arraycopy( keyLookup, 0, retval.keyLookup, 0, nrkeys );
    System.arraycopy( keyCondition, 0, retval.keyCondition, 0, nrkeys );
    System.arraycopy( keyStream2, 0, retval.keyStream2, 0, nrkeys );

    return retval;
  }

  private void readData( Node transformNode, IHopMetadataProvider metadataProvider ) throws HopXmlException {
    try {
      String csize;
      int nrkeys;

      String con = XmlHandler.getTagValue( transformNode, "connection" );
      databaseMeta = DatabaseMeta.loadDatabase( metadataProvider, con );
      csize = XmlHandler.getTagValue( transformNode, "commit" );
      commitSize = ( csize != null ) ? csize : "0";
      schemaName = XmlHandler.getTagValue( transformNode, "lookup", "schema" );
      tableName = XmlHandler.getTagValue( transformNode, "lookup", "table" );

      Node lookup = XmlHandler.getSubNode( transformNode, "lookup" );
      nrkeys = XmlHandler.countNodes( lookup, "key" );

      allocate( nrkeys );

      for ( int i = 0; i < nrkeys; i++ ) {
        Node knode = XmlHandler.getSubNodeByNr( lookup, "key", i );

        keyStream[ i ] = XmlHandler.getTagValue( knode, "name" );
        keyLookup[ i ] = XmlHandler.getTagValue( knode, "field" );
        keyCondition[ i ] = XmlHandler.getTagValue( knode, "condition" );
        if ( keyCondition[ i ] == null ) {
          keyCondition[ i ] = "=";
        }
        keyStream2[ i ] = XmlHandler.getTagValue( knode, "name2" );
      }

    } catch ( Exception e ) {
      throw new HopXmlException( BaseMessages.getString(
        PKG, "DeleteMeta.Exception.UnableToReadTransformMetaFromXML" ), e );
    }
  }

  public void setDefault() {
    keyStream = null;
    databaseMeta = null;
    commitSize = "100";
    schemaName = "";
    tableName = BaseMessages.getString( PKG, "DeleteMeta.DefaultTableName.Label" );

    int nrkeys = 0;

    allocate( nrkeys );
  }

  public String getXml() {
    StringBuilder retval = new StringBuilder( 500 );

    retval
      .append( "    " ).append(
      XmlHandler.addTagValue( "connection", databaseMeta == null ? "" : databaseMeta.getName() ) );
    retval.append( "    " ).append( XmlHandler.addTagValue( "commit", commitSize ) );
    retval.append( "    <lookup>" ).append( Const.CR );
    retval.append( "      " ).append( XmlHandler.addTagValue( "schema", schemaName ) );
    retval.append( "      " ).append( XmlHandler.addTagValue( "table", tableName ) );

    for ( int i = 0; i < keyStream.length; i++ ) {
      retval.append( "      <key>" ).append( Const.CR );
      retval.append( "        " ).append( XmlHandler.addTagValue( "name", keyStream[ i ] ) );
      retval.append( "        " ).append( XmlHandler.addTagValue( "field", keyLookup[ i ] ) );
      retval.append( "        " ).append( XmlHandler.addTagValue( "condition", keyCondition[ i ] ) );
      retval.append( "        " ).append( XmlHandler.addTagValue( "name2", keyStream2[ i ] ) );
      retval.append( "      </key>" ).append( Const.CR );
    }

    retval.append( "    </lookup>" ).append( Const.CR );

    return retval.toString();
  }

  public void getFields( IRowMeta rowMeta, String origin, IRowMeta[] info, TransformMeta nextTransform,
                         IVariables variables, IHopMetadataProvider metadataProvider ) throws HopTransformException {
    // Default: nothing changes to rowMeta
  }

  public void check( List<ICheckResult> remarks, PipelineMeta pipelineMeta, TransformMeta transformMeta,
                     IRowMeta prev, String[] input, String[] output, IRowMeta info, IVariables variables,
                     IHopMetadataProvider metadataProvider ) {
    CheckResult cr;
    String errorMessage = "";

    if ( databaseMeta != null ) {
      Database db = new Database( loggingObject, databaseMeta );
      db.shareVariablesWith( pipelineMeta );
      try {
        db.connect();

        if ( !Utils.isEmpty( tableName ) ) {
          cr =
            new CheckResult( ICheckResult.TYPE_RESULT_OK, BaseMessages.getString(
              PKG, "DeleteMeta.CheckResult.TablenameOK" ), transformMeta );
          remarks.add( cr );

          boolean first = true;
          boolean errorFound = false;
          errorMessage = "";

          // Check fields in table
          IRowMeta r = db.getTableFieldsMeta( schemaName, tableName );
          if ( r != null ) {
            cr =
              new CheckResult( ICheckResult.TYPE_RESULT_OK, BaseMessages.getString(
                PKG, "DeleteMeta.CheckResult.VisitTableSuccessfully" ), transformMeta );
            remarks.add( cr );

            for ( int i = 0; i < keyLookup.length; i++ ) {
              String lufield = keyLookup[ i ];

              IValueMeta v = r.searchValueMeta( lufield );
              if ( v == null ) {
                if ( first ) {
                  first = false;
                  errorMessage +=
                    BaseMessages.getString( PKG, "DeleteMeta.CheckResult.MissingCompareFieldsInTargetTable" )
                      + Const.CR;
                }
                errorFound = true;
                errorMessage += "\t\t" + lufield + Const.CR;
              }
            }
            if ( errorFound ) {
              cr = new CheckResult( ICheckResult.TYPE_RESULT_ERROR, errorMessage, transformMeta );
            } else {
              cr =
                new CheckResult( ICheckResult.TYPE_RESULT_OK, BaseMessages.getString(
                  PKG, "DeleteMeta.CheckResult.FoundLookupFields" ), transformMeta );
            }
            remarks.add( cr );
          } else {
            errorMessage = BaseMessages.getString( PKG, "DeleteMeta.CheckResult.CouldNotReadTableInfo" );
            cr = new CheckResult( ICheckResult.TYPE_RESULT_ERROR, errorMessage, transformMeta );
            remarks.add( cr );
          }
        }

        // Look up fields in the input stream <prev>
        if ( prev != null && prev.size() > 0 ) {
          cr =
            new CheckResult(
              ICheckResult.TYPE_RESULT_OK, BaseMessages.getString(
              PKG, "DeleteMeta.CheckResult.ConnectedTransformSuccessfully", String.valueOf( prev.size() ) ),
              transformMeta );
          remarks.add( cr );

          boolean first = true;
          errorMessage = "";
          boolean errorFound = false;

          for ( int i = 0; i < keyStream.length; i++ ) {
            IValueMeta v = prev.searchValueMeta( keyStream[ i ] );
            if ( v == null ) {
              if ( first ) {
                first = false;
                errorMessage += BaseMessages.getString( PKG, "DeleteMeta.CheckResult.MissingFields" ) + Const.CR;
              }
              errorFound = true;
              errorMessage += "\t\t" + keyStream[ i ] + Const.CR;
            }
          }
          for ( int i = 0; i < keyStream2.length; i++ ) {
            if ( keyStream2[ i ] != null && keyStream2[ i ].length() > 0 ) {
              IValueMeta v = prev.searchValueMeta( keyStream2[ i ] );
              if ( v == null ) {
                if ( first ) {
                  first = false;
                  errorMessage +=
                    BaseMessages.getString( PKG, "DeleteMeta.CheckResult.MissingFields2" ) + Const.CR;
                }
                errorFound = true;
                errorMessage += "\t\t" + keyStream[ i ] + Const.CR;
              }
            }
          }
          if ( errorFound ) {
            cr = new CheckResult( ICheckResult.TYPE_RESULT_ERROR, errorMessage, transformMeta );
          } else {
            cr =
              new CheckResult( ICheckResult.TYPE_RESULT_OK, BaseMessages.getString(
                PKG, "DeleteMeta.CheckResult.AllFieldsFound" ), transformMeta );
          }
          remarks.add( cr );

          // How about the fields to insert/update the table with?
          first = true;
          errorFound = false;
          errorMessage = "";
        } else {
          errorMessage = BaseMessages.getString( PKG, "DeleteMeta.CheckResult.MissingFields3" ) + Const.CR;
          cr = new CheckResult( ICheckResult.TYPE_RESULT_ERROR, errorMessage, transformMeta );
          remarks.add( cr );
        }
      } catch ( HopException e ) {
        errorMessage = BaseMessages.getString( PKG, "DeleteMeta.CheckResult.DatabaseError" ) + e.getMessage();
        cr = new CheckResult( ICheckResult.TYPE_RESULT_ERROR, errorMessage, transformMeta );
        remarks.add( cr );
      } finally {
        db.disconnect();
      }
    } else {
      errorMessage = BaseMessages.getString( PKG, "DeleteMeta.CheckResult.InvalidConnection" );
      cr = new CheckResult( ICheckResult.TYPE_RESULT_ERROR, errorMessage, transformMeta );
      remarks.add( cr );
    }

    // See if we have input streams leading to this transform!
    if ( input.length > 0 ) {
      cr =
        new CheckResult( ICheckResult.TYPE_RESULT_OK, BaseMessages.getString(
          PKG, "DeleteMeta.CheckResult.TransformReceivingInfo" ), transformMeta );
      remarks.add( cr );
    } else {
      cr =
        new CheckResult( ICheckResult.TYPE_RESULT_ERROR, BaseMessages.getString(
          PKG, "DeleteMeta.CheckResult.NoInputReceived" ), transformMeta );
      remarks.add( cr );
    }
  }

  public SqlStatement getSqlStatements( PipelineMeta pipelineMeta, TransformMeta transformMeta, IRowMeta prev,
                                        IHopMetadataProvider metadataProvider ) {
    SqlStatement retval = new SqlStatement( transformMeta.getName(), databaseMeta, null ); // default: nothing to do!

    if ( databaseMeta != null ) {
      if ( prev != null && prev.size() > 0 ) {
        if ( !Utils.isEmpty( tableName ) ) {
          Database db = new Database( loggingObject, databaseMeta );
          db.shareVariablesWith( pipelineMeta );
          try {
            db.connect();

            String schemaTable = databaseMeta.getQuotedSchemaTableCombination( schemaName, tableName );
            String crTable = db.getDDL( schemaTable, prev, null, false, null, true );

            String crIndex = "";
            String[] idxFields = null;

            if ( keyLookup != null && keyLookup.length > 0 ) {
              idxFields = new String[ keyLookup.length ];
              for ( int i = 0; i < keyLookup.length; i++ ) {
                idxFields[ i ] = keyLookup[ i ];
              }
            } else {
              retval.setError( BaseMessages.getString( PKG, "DeleteMeta.CheckResult.KeyFieldsRequired" ) );
            }

            // Key lookup dimensions...
            if ( idxFields != null && idxFields.length > 0 && !db.checkIndexExists( schemaTable, idxFields ) ) {
              String indexname = "idx_" + tableName + "_lookup";
              crIndex =
                db.getCreateIndexStatement(
                  schemaName, tableName, indexname, idxFields, false, false, false, true );
            }

            String sql = crTable + crIndex;
            if ( sql.length() == 0 ) {
              retval.setSql( null );
            } else {
              retval.setSql( sql );
            }
          } catch ( HopException e ) {
            retval.setError( BaseMessages.getString( PKG, "DeleteMeta.Returnvalue.ErrorOccurred" )
              + e.getMessage() );
          }
        } else {
          retval.setError( BaseMessages.getString( PKG, "DeleteMeta.Returnvalue.NoTableDefinedOnConnection" ) );
        }
      } else {
        retval.setError( BaseMessages.getString( PKG, "DeleteMeta.Returnvalue.NoReceivingAnyFields" ) );
      }
    } else {
      retval.setError( BaseMessages.getString( PKG, "DeleteMeta.Returnvalue.NoConnectionDefined" ) );
    }

    return retval;
  }

  public void analyseImpact( List<DatabaseImpact> impact, PipelineMeta pipelineMeta, TransformMeta transformMeta,
                             IRowMeta prev, String[] input, String[] output, IRowMeta info,
                             IHopMetadataProvider metadataProvider ) throws HopTransformException {
    if ( prev != null ) {
      // Lookup: we do a lookup on the natural keys
      for ( int i = 0; i < keyLookup.length; i++ ) {
        IValueMeta v = prev.searchValueMeta( keyStream[ i ] );

        DatabaseImpact ii =
          new DatabaseImpact(
            DatabaseImpact.TYPE_IMPACT_DELETE, pipelineMeta.getName(), transformMeta.getName(), databaseMeta
            .getDatabaseName(), tableName, keyLookup[ i ], keyStream[ i ],
            v != null ? v.getOrigin() : "?", "", "Type = " + v.toStringMeta() );
        impact.add( ii );
      }
    }
  }

  public Delete createTransform( TransformMeta transformMeta, DeleteData data, int cnr, PipelineMeta tr,
                                 Pipeline pipeline ) {
    return new Delete( transformMeta, this, data, cnr, tr, pipeline );
  }

  public DeleteData getTransformData() {
    return new DeleteData();
  }

  public DatabaseMeta[] getUsedDatabaseConnections() {
    if ( databaseMeta != null ) {
      return new DatabaseMeta[] { databaseMeta };
    } else {
      return super.getUsedDatabaseConnections();
    }
  }

  /**
   * @return the schemaName
   */
  public String getSchemaName() {
    return schemaName;
  }

  /**
   * @param schemaName the schemaName to set
   */
  public void setSchemaName( String schemaName ) {
    this.schemaName = schemaName;
  }

  public boolean supportsErrorHandling() {
    return true;
  }

}
