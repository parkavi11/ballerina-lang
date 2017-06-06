/**
 * Copyright (c) 2016, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
import _ from 'lodash';
import log from 'log';
import ConditionalStatement from './conditional-statement';
import FragmentUtils from '../../utils/fragment-utils';

/**
 * Class for if conditions in ballerina. Extended from Conditional-Statement
 * @constructor
 */
class ElseIfStatement extends ConditionalStatement {
    constructor(args) {
        super();
        if(!_.isNil(_.get(args, 'condition'))){
            this._condition = _.get(args, 'condition');
        }
        this.type = "ElseIfStatement";
        this.whiteSpace.defaultDescriptor.regions = {
            0: '',
            1: ' ',
            2: ' ',
            3: '',
            4: ' ',
            5: '\n',
            6: ' '
        };
    }

    setConditionFromString(conditionString) {
        if(!_.isNil(conditionString)){
            let fragment = FragmentUtils.createExpressionFragment(conditionString);
            let parsedJson = FragmentUtils.parseFragment(fragment);
            let condition = this.getFactory().createFromJson(parsedJson);
            condition.initFromJson(parsedJson);
            this.setCondition(condition);
            condition.setParent(this);
        }
    }

    setCondition(condition, options) {
        if(!_.isNil(condition)){
            this.setAttribute('_condition', condition, options);
        }
    }

    getCondition() {
        return this._condition;
    }

    initFromJson(jsonNode) {
        if (!_.isNil(jsonNode.condition)) {
            let condition = this.getFactory().createFromJson(jsonNode.condition);
            condition.initFromJson(jsonNode.condition);
            this.setCondition(condition);
            condition.setParent(this);
        }
        _.each(jsonNode.children, (childNode) => {
            var child = undefined;
            // FIXME Keeping existing fragile logic to detect connector declaration as it is for now. We should refactor this
            if (childNode.type === "variable_definition_statement" &&
                !_.isNil(childNode.children[1]) && childNode.children[1].type === 'connector_init_expr') {
                child = this.getFactory().createConnectorDeclaration();
            } else {
                child = this.getFactory().createFromJson(childNode);
            }
            this.addChild(child);
            child.initFromJson(childNode);
        });
    }
}

export default ElseIfStatement;
