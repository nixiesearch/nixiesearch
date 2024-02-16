$(document).ready(function() {
    var index = $('#suggest').val()
    var loadRemote = new Bloodhound({
        datumTokenizer: Bloodhound.tokenizers.whitespace,
        queryTokenizer: Bloodhound.tokenizers.whitespace,
        remote: {
            url: '_ui/_suggest?query=%QUERY&index='+index,
            wildcard: '%QUERY'
        }
    });

    $('.typeahead').typeahead(null,
    {
      name: 'suggestions',
      display: 'value',
      source: loadRemote
    });
});

