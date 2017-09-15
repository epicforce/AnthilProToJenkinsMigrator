// Javascript file for handling the ahp2jenkins plugin page
// 'rpc' must be defined prior to including this file.

jQuery(document).ready(function() {
    // Disable form submit
    jQuery('form').submit(function(ev) {
        ev.preventDefault();
        return false;
    });

    // Wire in javascript handlers to dynamically build this page.
    // Use the 'rpc' provided by Jenkins to do calls as needed.

    // Common method to check for and return the instance if its
    // been selected.  Pops up an alert if no instance has been
    // picked (this shouldn't be possible)
    function getInstance() {
        var instance = jQuery('#ahpInstance').val();

        if((!instance) || (!instance.length)) {
            alert("You must pick an instance first.");
            return false;
        }

        return instance;
    }

    // Toggle visibility on select
    jQuery('#ahpInstance').change(function(ev) {
        if(jQuery(this).val() && jQuery(this).val().length) {
            jQuery('#workflowPicker').show();
        } else {
            jQuery('#workflowPicker').hide();
        }
    });

    // Perform project search
    jQuery('#projectSearch').click(function(ev) {
        ev.preventDefault();

        // Get our instance
        var ahpInstance = getInstance();

        if(!ahpInstance) {
            return false;
        }

        // Get the text, complain if its not there.
        var searchQuery = jQuery('#searchText').val();

        if((!searchQuery) || (!searchQuery.length)) {
            alert('Please provide an Anthill Pro project name to search for.');
            return false;
        }

        // Start our loading spinner
        jQuery('#loadingSpinner').show();

        // Use RPC to search for it.  This will return from this
        // click callback right away because Jenkins RPC calls are
        // async.
        rpc.doSearch(ahpInstance, searchQuery, function(res) {
            projectMap = res.responseObject();

            // Check if null
            if(!projectMap) {
                alert('We had a problem connecting to Anthill Pro.  Please '
                      + 'check your host, port and credentials, then try again'
                );

                // Turn off spinner
                jQuery('#loadingSpinner').hide();

                return;
            }

            // Show the result area
            jQuery('.displayOnSearch').show();

            // Where the select boxes go
            var sbTarget = jQuery('#selectWorkflowsList');
            sbTarget.empty();

            // Populate select boxes accordingly.
            if(jQuery.isEmptyObject(projectMap)){
                sbTarget.html(
                    "<strong>Search returned no results.  Try again.</strong>"
                );
            } else {
                jQuery.each(projectMap, function(projectName, wfs) {
                    // Use our project name in the header.  We do it this
                    // way to escape any crap in the project name.
                    var header = jQuery('<h5 class="section-header" />');
                    header.text(projectName);
                    sbTarget.append(header);

                    // Add in our checkboxes
                    jQuery.each(wfs, function(wfName, wfId) {
                        var newDiv = jQuery('<div />');
                        newDiv.append(
                            '<input type="checkbox" value="' + wfId + '" />'
                        );

                        var newSpan = jQuery('<span />');
                        newSpan.text(projectName + " - " + wfName);
                        newDiv.append(newSpan);
                        sbTarget.append(newDiv);
                    });
                });
            }

            jQuery('#loadingSpinner').hide();
        });

        return false;
    });

    // Checkbox click handler.
    jQuery('#selectWorkflowsList').on('click', 'input[type="checkbox"]',
                                      function(ev) {
        // Move this div down
        jQuery(this).parent().detach().appendTo('#selectedWorkflowsList');
    });

    // And click to remove
    jQuery('#selectedWorkflowsList').on('click', 'input[type="checkbox"]',
                                        function(ev) {
        jQuery(this).parent().remove();
    });

    // Trigger migration
    jQuery('#migrate').click(function(ev) {
        ev.preventDefault();

        // See if anything is checked
        var items = jQuery('#selectedWorkflowsList input:checked');

        if(!items.length) {
            alert("Please select workflows to migrate, first.");
            return false;
        }

        // get array of values
        var vals = items.map(function() { return jQuery(this).val(); }).get();

        var instance = getInstance();

        if(instance) {
            rpc.doSubmitWorkflows(getInstance(), vals);
        }

        return false;
    });

    // Function to populate status table
    function checkStatus()
    {
        rpc.doStatus(function(ret) {
            var stat = ret.responseObject();
            var statTab = jQuery('#ahpJobs tbody');
            statTab.empty();

            if((!stat) || (!stat.length)) {
                statTab.append(
                    '<tr><td colspan="3">No migrations yet!</td></tr>'
                );
            } else {
                jQuery.each(stat, function(k, val) {
                    var newRow = jQuery('<tr />');

                    for(var i = 0; i < val.length; i++) {
                        var newCell = jQuery('<td />');
                        newCell.text(val[i]);
                        newRow.append(newCell);
                    }

                    statTab.append(newRow);
                });
            }

            setTimeout(checkStatus, 5000);
        });
    }

    checkStatus();
});
