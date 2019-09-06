// Copyright (c) 2019 WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
//
// WSO2 Inc. licenses this file to you under the Apache License,
// Version 2.0 (the "License"); you may not use this file except
// in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

# Type for XML options.
#
# + attributePrefix - attribute prefix of JSON
# + arrayEntryTag - array entry tag of JSON
public type XmlOptions record {
    string attributePrefix = "@";
    string arrayEntryTag = "root";
};

# Converts a JSON object to a XML representation.
#
# + x - The json source
# + options - jsonOptions struct for JSON to XML conversion properties
# + return - XML representation of the given JSON
public function toXML(json x, XmlOptions options = {}) returns xml|error = external;
