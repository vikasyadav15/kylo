/*-
 * #%L
 * thinkbig-ui-feed-manager
 * %%
 * Copyright (C) 2017 ThinkBig Analytics
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
(function () {

    var directive = function () {
        return {
            restrict: "EA",
            bindToController: {
                selectedTabIndex:'='
            },
            controllerAs: 'vm',
            scope: {},
            templateUrl: 'js/feed-details/profile-history/profile-history.html',
            controller: "FeedProfileHistoryController",
            link: function ($scope, element, attrs, controller) {

            }

        };
    }

    var controller =  function($scope,$http,$mdDialog, $filter, FeedService, RestUrlService, HiveService, StateService, Utils) {

        var self = this;

        this.model = FeedService.editFeedModel;
        this.showSummary = true;
        this.profileSummary = [];
        this.loading = false;
        function getProfileHistory(){
            self.loading = true;
            self.showNoResults = false;
            var successFn = function (response) {
                if (response.data.length == 0) {
                    self.showNoResults = true;
                }
            var dataMap = {};
                var dataArr = [];
             var columns = HiveService.getColumnNamesForQueryResult(response);
                 if(columns != null) {
                     //get the keys into the map for the different columns
                    var dateColumn = _.find(columns,function(column){
                        return Utils.strEndsWith(column,'processing_dttm');
                    });

                     var metricTypeColumn =  _.find(columns,function(column){
                         return Utils.strEndsWith(column,'metrictype');
                     });

                     var metricValueColumn = _.find(columns,function(column){
                         return Utils.strEndsWith(column,'metricvalue');
                     });

                     //group on date column
                 angular.forEach(response.data,function(row){

                     if(dataMap[row[dateColumn]] == undefined){
                         var timeInMillis = HiveService.getUTCTime(row[dateColumn]);
                         var obj = {'PROCESSING_DTTM':row[dateColumn],'DATE_TIME':timeInMillis,'DATE':new Date(timeInMillis)};
                         dataMap[row[dateColumn]] = obj;
                         dataArr.push(obj);
                     }
                     var newRow = dataMap[row[dateColumn]];
                     var metricType = row[metricTypeColumn];
                     var value = row[metricValueColumn];
                     if(value && metricType == 'MIN_TIMESTAMP' || metricType == 'MAX_TIMESTAMP'){
                         //first check to see if it is millis
                         if(!isNaN(value)){
                             var dateStr = $filter('date')(new Date(tmp),"yyyy-MM-dd");
                             value = dateStr;
                         }
                         else {
                             value = value.substr(0,10); //string the time off the string
                         }

                      }

                     newRow[metricType] = value;
                 }) ;

                     //sort it desc

                    // dataArr = _.sortBy(dataArr,dateColumn).reverse();
                     dataArr = _.sortBy(dataArr,'DATE_TIME').reverse();

                     self.profileSummary = dataArr;


            }
                self.loading = false;

            }
            var errorFn = function (err) {
                console.log('ERROR ',err)
                self.loading = false;
            }
            var promise = $http.get(RestUrlService.FEED_PROFILE_SUMMARY_URL(self.model.id));
            promise.then(successFn, errorFn);
            return promise;
        }

        this.onValidCountClick = function(row){
          self.showProfileDialog('valid',row);
        }
        this.onInvalidCountClick = function(row){
           self.showProfileDialog('invalid',row);
        }

        this.viewProfileStats = function(row) {
            self.showProfileDialog('profile-stats',row);
        }

        this.showProfileDialog = function(currentTab,profileRow) {
            $mdDialog.show({
                controller: 'FeedProfileItemController',
                templateUrl: 'js/feed-details/profile-history/profile-history-dialog.html',
                parent: angular.element(document.body),
                clickOutsideToClose:false,
                fullscreen: true,
                locals : {
                    feed:self.model,
                    profileRow:profileRow,
                    currentTab:currentTab
                }
            }).then(function(msg) {

                }, function() {

                });
        };




        getProfileHistory();
    };


    angular.module(MODULE_FEED_MGR).controller('FeedProfileHistoryController', controller);

    angular.module(MODULE_FEED_MGR)
        .directive('thinkbigFeedProfileHistory', directive);

})();






(function () {

    var controller = function($scope, $mdDialog, $mdToast, $http, StateService,FeedService, BroadcastService, feed,profileRow,currentTab){
        $scope.feed = feed;
        $scope.profileRow = profileRow;
        $scope.processingdate = $scope.profileRow['DATE'];

        $scope.processingdttm = $scope.profileRow['PROCESSING_DTTM']
        if(currentTab == 'valid') {
            $scope.selectedTabIndex = 1;
        }
        else if(currentTab == 'invalid') {
            $scope.selectedTabIndex = 2;
        }
        if(currentTab == 'profile-stats') {
            $scope.selectedTabIndex = 0;
        }

        $scope.hide = function($event) {
            $mdDialog.hide();
        };

        $scope.done = function($event) {
            $mdDialog.hide();
        };

        $scope.cancel = function($event) {
            $mdDialog.hide();
        };

        $scope.renderPagination = false;
        BroadcastService.subscribe($scope,'PROFILE_TAB_DATA_LOADED',function(tab) {
            $scope.renderPagination = true;
        });


        //Pagination DAta
        $scope.paginationData = {rowsPerPage:50,
            currentPage:1,
            rowsPerPageOptions:['5','10','20','50','100']}

        $scope.paginationId = 'profile_stats_0';
        $scope.$watch('selectedTabIndex',function(newVal){
            $scope.renderPagination = false;
            $scope.paginationId = 'profile_stats_'+newVal;
            $scope.paginationData.currentPage = 1;
        });




        $scope.onPaginationChange = function (page, limit) {
            $scope.paginationData.currentPage = page;
        };



    };

    angular.module(MODULE_FEED_MGR).controller('FeedProfileItemController',controller);



}());


(function () {

    var directive = function ($window,$timeout) {
        return {
            restrict: "A",
            scope:{},
            link: function ($scope, $element, attrs) {

                function onResize() {
                    var $w = angular.element($window);
                    var height = $w.height();
                    if (attrs.parent) {
                        height = angular.element(attrs.parent).height();
                    }
                    if (attrs.offsetHeight) {
                        height -= parseInt(attrs.offsetHeight);
                    }
                    if(height <=0){
                        $timeout(function(){
                            onResize();
                        },10)
                    }
                    else {
                        $element.css('height', height);
                        $element.css('overflow', 'auto')
                    }
                }

                angular.element($window).on('resize.profilehistory', function() {
                    onResize();
                });
                onResize();

                $scope.$on('destroy',function() {
                    angular.element($window).unbind('resize.profilehistory');
                })

            }

        };
    }




    angular.module(MODULE_FEED_MGR)
        .directive('overflowScroll', ['$window','$timeout',directive]);

})();
