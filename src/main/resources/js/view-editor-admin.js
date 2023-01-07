(function ($) {

    var url = AJS.contextPath() + "/rest/view-editor-admin/1.0/environments";

    $(function() {
        $.ajax({
            url: url,
            dataType: "json"
        }).done(function(environments) {
            var table = $("#environments");
            environments.forEach(function(environment) {
              var row = $("<tr><td class=\"identifier\"></td><td class=\"viewer-uri\"></td><td class=\"space-allowlist\">asdf</td><td class=\"action\"><form class=\"delete-environment\"><input type=\"submit\" name=\"delete\" value=\"Delete\"></td></tr>");

              row.find(".identifier").text(environment.id);
              row.find(".viewer-uri").text(environment.viewerUri);
              console.log(environment.spaceAllowlist);
              row.find(".space-allowlist").text(environment.spaceAllowlist);
              row.find(".delete-environment").on("submit", function(e) {
                  e.preventDefault();
                  $.ajax({
                      url: url + "/" + environment.id,
                      type: "DELETE"
                  }).done(function(environment, textStatus, jqXHR) {
                      location.reload(true);
                  });
              });

              table.append(row);
            });
        });

        function handleAjaxError(jqXHR, textStatus, errorThrown) {
          var response = $.parseJSON(jqXHR.responseText);
          console.log(response);
          var message;
          if (response && response.error) {
            message = response.error;
          } else {
            message = "An unexpected error occurred when trying to add the specified environment."
          }
          $("#add-environment-error").val(message);
        }

        function showError(message) {
          $("#add-environment-error").text(message);
          $("#add-environment-error-container").css("display", "");
        }

        function addEnvironment() {
          var id = $("#id").val();
          if (!id) {
            showError("Identifier must be provided.");
            return;
          }
          $.ajax({
              url: url + "/" + id,
              dataType: "json",
              type: "PUT",
              contentType: "application/json",
              data: JSON.stringify({
                  id: $("#id").val(),
                  viewerUri: $("#viewer-uri").val(),
                  spaceAllowlist: $("#space-allowlist").val(),
              }),
              processData: false
          }).done(function(environment, textStatus, jqXHR) {
              location.reload(true);
          }).fail(function(jqXHR, textStatus, errorThrown) {
              var response = $.parseJSON(jqXHR.responseText);
              var message;
              if (response && response.error) {
                message = response.error;
              } else {
                message = "An unexpected error occurred when trying to add the specified environment."
              }
              showError(message);
          });
        }

        $("#add-environment").on("submit", function(e) {
          e.preventDefault();
          addEnvironment();
        });
    });
})(AJS.$ || jQuery);
