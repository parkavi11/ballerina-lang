import module1;

function getStringInPkg() returns (string){
    int a = 12;
    
    match expr {
        var (s, i) if s is string => printMessage();
        var (s, i) => {
            printMessage();
        }
        {x: 12, y: "B"} => p
        12 => printMessage();
    }
    
    int b = 12;
}
